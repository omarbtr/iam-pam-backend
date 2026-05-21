package com.iam.pam.service;

import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.Resource;
import com.iam.pam.entity.WebAuditLog;
import com.iam.pam.repository.AccessRequestRepository;
import com.iam.pam.repository.WebAuditLogRepository;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class WebProxyService {

    private static final Logger log = LoggerFactory.getLogger(WebProxyService.class);

    private static final Set<String> STRIP_REQUEST_HEADERS = Set.of(
            "host", "connection", "keep-alive", "upgrade",
            "proxy-connection", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "content-length",
            "x-pam-user", "x-pam-resource"
    );

    private static final Set<String> STRIP_RESPONSE_HEADERS = Set.of(
            "transfer-encoding", "connection", "keep-alive",
            "content-length",
            "x-frame-options",
            ":status"
    );

    private final AccessRequestRepository accessRequestRepository;
    private final AuditService auditService;
    private final AccessRequestService accessRequestService;
    private final WebAuditLogRepository webAuditLogRepository;

    private final Map<String, ActiveWebSession> activeSessions = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WebProxyService(AccessRequestRepository accessRequestRepository,
                           AuditService auditService,
                           AccessRequestService accessRequestService,
                           WebAuditLogRepository webAuditLogRepository) {
        this.accessRequestRepository = accessRequestRepository;
        this.auditService = auditService;
        this.accessRequestService = accessRequestService;
        this.webAuditLogRepository = webAuditLogRepository;
    }

    /**
     * Validates the access request, creates a UUID session token, and stores it.
     * @return session UUID
     */
    public String startSession(Long accessRequestId) {
        AccessRequest request = accessRequestRepository.findByIdWithResource(accessRequestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Access request not found: " + accessRequestId));

        if (!request.isActive()) {
            throw new IllegalStateException("Access request is not active or has expired");
        }

        Resource resource = request.getResource();
        String host = resource.getHost();
        int port = resource.getPort() != null ? resource.getPort() : 80;

        validatePrivateNetwork(host);

        String internalUrl = "http://" + host + ":" + port;
        String sessionId = UUID.randomUUID().toString();
        String username = request.getRequesterUsername();
        String tenantId = request.getTenantId();
        String resourceName = resource.getName();

        activeSessions.put(sessionId, new ActiveWebSession(
                sessionId, accessRequestId, internalUrl, username, tenantId, resourceName));

        accessRequestService.recordFirstAccess(accessRequestId);
        auditService.log(AuditLog.AuditAction.SESSION_STARTED,
                username, tenantId, resourceName, accessRequestId,
                "Web proxy session opened to " + internalUrl);
        webAuditLogRepository.save(new WebAuditLog(accessRequestId, username, tenantId,
                resourceName, WebAuditLog.EventType.SESSION_START, "Proxy to " + internalUrl));

        log.info("Web proxy session {} started for request {} → {}", sessionId, accessRequestId, internalUrl);
        return sessionId;
    }

    /**
     * Proxies an HTTP request to the internal app and returns the response.
     */
    public ResponseEntity<byte[]> proxy(String sessionId, String subPath, HttpServletRequest servletRequest) {
        ActiveWebSession session = activeSessions.get(sessionId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .body("Session expirée ou invalide.".getBytes(StandardCharsets.UTF_8));
        }

        String queryString = servletRequest.getQueryString();
        String path = "/" + (subPath != null ? subPath : "");
        String targetUrl = session.internalUrl() + path
                + (queryString != null ? "?" + queryString : "");

        log.debug("Proxying {} {} → {}", servletRequest.getMethod(), servletRequest.getRequestURI(), targetUrl);

        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(30));

            Enumeration<String> headerNames = servletRequest.getHeaderNames();
            while (headerNames != null && headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (!STRIP_REQUEST_HEADERS.contains(name.toLowerCase())) {
                    try {
                        rb.header(name, servletRequest.getHeader(name));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            // Inject PAM user identity
            rb.header("X-Pam-User",     session.username());
            rb.header("X-Pam-Resource", session.resourceName());

            String method = servletRequest.getMethod().toUpperCase();
            byte[] body = new byte[0];
            try {
                body = servletRequest.getInputStream().readAllBytes();
            } catch (IOException ignored) {}

            HttpRequest.BodyPublisher publisher = body.length > 0
                    ? HttpRequest.BodyPublishers.ofByteArray(body)
                    : HttpRequest.BodyPublishers.noBody();

            if ("GET".equals(method) || "HEAD".equals(method)
                    || "DELETE".equals(method) || "OPTIONS".equals(method)) {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                rb.method(method, publisher);
            }

            HttpResponse<byte[]> response = httpClient.send(
                    rb.build(), HttpResponse.BodyHandlers.ofByteArray());

            HttpHeaders responseHeaders = new HttpHeaders();
            response.headers().map().forEach((k, values) -> {
                if (!STRIP_RESPONSE_HEADERS.contains(k.toLowerCase())) {
                    responseHeaders.addAll(k, values);
                }
            });

            byte[] responseBody = response.body();
            String contentType = response.headers().firstValue("content-type").orElse("");

            if (contentType.contains("text/html")) {
                Charset charset = extractCharset(contentType);
                String html = new String(responseBody, charset);
                html = rewriteHtml(html, sessionId, session.internalUrl());
                responseBody = html.getBytes(charset);
            }

            String location = response.headers().firstValue("location").orElse(null);
            if (location != null) {
                responseHeaders.set("Location",
                        rewriteUrl(location, sessionId, session.internalUrl()));
            }

            return ResponseEntity.status(response.statusCode())
                    .headers(responseHeaders)
                    .body(responseBody);

        } catch (Exception e) {
            log.error("Web proxy error for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(("Erreur proxy: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Ends a web proxy session and logs the audit trail.
     */
    public void endSession(String sessionId) {
        ActiveWebSession session = activeSessions.remove(sessionId);
        if (session == null) return;
        auditService.log(AuditLog.AuditAction.SESSION_ENDED,
                session.username(), session.tenantId(), session.resourceName(),
                session.accessRequestId(), "Web proxy session closed");
        webAuditLogRepository.save(new WebAuditLog(session.accessRequestId(),
                session.username(), session.tenantId(), session.resourceName(),
                WebAuditLog.EventType.SESSION_END, "Web proxy session closed"));
        log.info("Web proxy session {} closed", sessionId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validatePrivateNetwork(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (!addr.isLoopbackAddress() && !addr.isSiteLocalAddress() && !addr.isLinkLocalAddress()) {
                throw new IllegalArgumentException(
                        "Target host must be a private network address: " + host);
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve target host: " + host);
        }
    }

    /** Injects {@code <base href>} and rewrites absolute internal-host URLs. */
    private String rewriteHtml(String html, String sessionId, String internalUrl) {
        String proxyBase = "/proxy/web/" + sessionId + "/";

        if (!html.contains("<base ") && !html.contains("<BASE ")) {
            if (html.contains("<head>")) {
                html = html.replace("<head>", "<head><base href=\"" + proxyBase + "\">");
            } else if (html.contains("<HEAD>")) {
                html = html.replace("<HEAD>", "<HEAD><base href=\"" + proxyBase + "\">");
            }
        }

        String esc = Pattern.quote(internalUrl);
        html = html.replaceAll("(?i)(href|src|action)=\"" + esc + "([^\"]*?)\"",
                "$1=\"" + proxyBase + "$2\"");
        html = html.replaceAll("(?i)(href|src|action)='" + esc + "([^']*?)'",
                "$1='" + proxyBase + "$2'");

        return html;
    }

    private String rewriteUrl(String url, String sessionId, String internalUrl) {
        String proxyBase = "/proxy/web/" + sessionId;
        if (url.startsWith(internalUrl)) {
            return proxyBase + url.substring(internalUrl.length());
        }
        if (url.startsWith("/")) {
            return proxyBase + url;
        }
        return url;
    }

    private Charset extractCharset(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        int idx = contentType.toLowerCase().indexOf("charset=");
        if (idx < 0) return StandardCharsets.UTF_8;
        String name = contentType.substring(idx + 8).trim().replace("\"", "");
        int semi = name.indexOf(';');
        if (semi >= 0) name = name.substring(0, semi).trim();
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    @PreDestroy
    public void shutdown() {
        new HashSet<>(activeSessions.keySet()).forEach(this::endSession);
    }

    private record ActiveWebSession(
            String sessionId,
            Long accessRequestId,
            String internalUrl,
            String username,
            String tenantId,
            String resourceName) {}
}
