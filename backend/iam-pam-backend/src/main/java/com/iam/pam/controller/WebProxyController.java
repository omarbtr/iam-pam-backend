package com.iam.pam.controller;

import com.iam.pam.service.WebProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class WebProxyController {

    private final WebProxyService webProxyService;

    public WebProxyController(WebProxyService webProxyService) {
        this.webProxyService = webProxyService;
    }

    /**
     * Starts a web proxy session.
     * Returns { sessionId, proxyUrl } where proxyUrl is the iframe src path.
     */
    @PostMapping("/api/pam/web/start/{requestId}")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin', 'pam-access')")
    public Map<String, String> startSession(@PathVariable Long requestId) {
        String sessionId = webProxyService.startSession(requestId);
        return Map.of(
                "sessionId", sessionId,
                "proxyUrl", "/proxy/web/" + sessionId + "/"
        );
    }

    /**
     * Proxies all HTTP methods on /proxy/web/{sessionId}/**
     * No JWT required — the sessionId UUID acts as the capability token.
     */
    @RequestMapping(
        value  = "/proxy/web/{sessionId}/**",
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                  RequestMethod.DELETE, RequestMethod.PATCH,
                  RequestMethod.HEAD,  RequestMethod.OPTIONS}
    )
    public ResponseEntity<byte[]> proxy(
            @PathVariable String sessionId,
            HttpServletRequest request) {

        // Extract the sub-path after /proxy/web/{sessionId}
        String fullPath = request.getRequestURI();
        String prefix   = "/proxy/web/" + sessionId;
        String subPath  = fullPath.startsWith(prefix)
                ? fullPath.substring(prefix.length())
                : "/";
        // Strip leading slash so the service can prepend its own
        if (subPath.startsWith("/")) subPath = subPath.substring(1);

        return webProxyService.proxy(sessionId, subPath, request);
    }

    /**
     * Stops a web proxy session and logs the audit trail.
     */
    @DeleteMapping("/api/pam/web/stop/{sessionId}")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin', 'pam-access')")
    public void stopSession(@PathVariable String sessionId) {
        webProxyService.endSession(sessionId);
    }
}
