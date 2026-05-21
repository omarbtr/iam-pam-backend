package com.iam.pam.service;

import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.RdpAuditLog;
import com.iam.pam.entity.Resource;
import com.iam.pam.repository.AccessRequestRepository;
import com.iam.pam.repository.RdpAuditLogRepository;
import jakarta.annotation.PreDestroy;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.net.GuacamoleTunnel;
import org.apache.guacamole.net.InetGuacamoleSocket;
import org.apache.guacamole.net.SimpleGuacamoleTunnel;
import org.apache.guacamole.protocol.ConfiguredGuacamoleSocket;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages RDP sessions through the bastion VM via guacd.
 * Flow: Browser <-WebSocket (Guacamole protocol)-> Spring Boot <-TCP:4822-> guacd <-RDP-> Target
 */
@Service
public class GuacamoleService {

    private static final Logger log = LoggerFactory.getLogger(GuacamoleService.class);

    @Value("${guacd.host}")
    private String guacdHost;

    @Value("${guacd.port:4822}")
    private int guacdPort;

    @Value("${guacd.recording-path:/var/recordings}")
    private String recordingBasePath;

    private final AccessRequestRepository accessRequestRepository;
    private final RdpAuditLogRepository rdpAuditLogRepository;
    private final AuditService auditService;
    private final AccessRequestService accessRequestService;

    private final Map<Long, ActiveRdpSession> activeSessions = new ConcurrentHashMap<>();

    public GuacamoleService(AccessRequestRepository accessRequestRepository,
                            RdpAuditLogRepository rdpAuditLogRepository,
                            AuditService auditService,
                            AccessRequestService accessRequestService) {
        this.accessRequestRepository = accessRequestRepository;
        this.rdpAuditLogRepository = rdpAuditLogRepository;
        this.auditService = auditService;
        this.accessRequestService = accessRequestService;
    }

    public void openSession(Long accessRequestId, WebSocketSession wsSession) throws Exception {
        AccessRequest request = accessRequestRepository.findByIdWithResource(accessRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + accessRequestId));

        if (!request.isActive()) {
            wsSession.sendMessage(new TextMessage("\r\n[IAM-PAM] Session expirée ou non approuvée.\r\n"));
            wsSession.close();
            return;
        }

        Resource resource = request.getResource();
        String targetHost = resource.getHost();
        int targetPort    = resource.getPort() != null ? resource.getPort() : 3389;
        String username   = resource.getCredentialUsername() != null ? resource.getCredentialUsername() : "";
        String password   = resource.getCredentialPassword() != null ? resource.getCredentialPassword() : "";

        String resourceName      = resource.getName();
        String requesterUsername = request.getRequesterUsername();
        String tenantId          = request.getTenantId();

        log.info("Opening RDP session for request {} to {}:{}", accessRequestId, targetHost, targetPort);

        GuacamoleConfiguration config = new GuacamoleConfiguration();
        config.setProtocol("rdp");
        config.setParameter("hostname", targetHost);
        config.setParameter("port", String.valueOf(targetPort));
        config.setParameter("username", username);
        config.setParameter("password", password);
        config.setParameter("width", "1280");
        config.setParameter("height", "720");
        config.setParameter("dpi", "96");
        config.setParameter("color-depth", "16");
        config.setParameter("ignore-cert", "true");
        config.setParameter("disable-auth", "false");

        // Enable session recording via guacd built-in recorder
        String recordingName = "rdp-" + accessRequestId + "-" + System.currentTimeMillis();
        config.setParameter("recording-path", recordingBasePath);
        config.setParameter("recording-name", recordingName);
        config.setParameter("recording-exclude-output", "false");
        config.setParameter("recording-exclude-mouse", "false");
        config.setParameter("create-recording-path", "true");

        InetGuacamoleSocket socket = new InetGuacamoleSocket(guacdHost, guacdPort);
        ConfiguredGuacamoleSocket configuredSocket = new ConfiguredGuacamoleSocket(socket, config);
        GuacamoleTunnel tunnel = new SimpleGuacamoleTunnel(configuredSocket);

        accessRequestService.recordFirstAccess(accessRequestId);
        accessRequestService.saveRecordingPath(accessRequestId, recordingBasePath + "/" + recordingName);
        auditService.log(AuditLog.AuditAction.SESSION_STARTED,
                requesterUsername, tenantId, resourceName, accessRequestId,
                "RDP session opened to " + targetHost + ":" + targetPort);

        rdpAuditLogRepository.save(new RdpAuditLog(accessRequestId, requesterUsername, tenantId,
                resourceName, RdpAuditLog.EventType.SESSION_START, "RDP to " + targetHost));

        ActiveRdpSession session = new ActiveRdpSession(tunnel, wsSession,
                requesterUsername, tenantId, resourceName);
        activeSessions.put(accessRequestId, session);

        // Relay guacd output to WebSocket
        Thread relay = new Thread(() -> relayToWs(tunnel, wsSession, accessRequestId));
        relay.setDaemon(true);
        relay.setName("guac-relay-" + accessRequestId);
        relay.start();
    }

    public void sendInstruction(Long accessRequestId, String instruction) {
        ActiveRdpSession session = activeSessions.get(accessRequestId);
        if (session == null) return;
        try {
            session.tunnel().acquireWriter().write(instruction.toCharArray());
            session.tunnel().releaseWriter();
        } catch (Exception e) {
            log.debug("Failed to write to guacd for session {}: {}", accessRequestId, e.getMessage());
        }
    }

    public void closeSession(Long accessRequestId) {
        ActiveRdpSession session = activeSessions.remove(accessRequestId);
        if (session == null) return;
        try {
            session.tunnel().close();
            log.info("RDP session {} closed", accessRequestId);
            auditService.log(AuditLog.AuditAction.SESSION_ENDED,
                    session.requesterUsername(), session.tenantId(),
                    session.resourceName(), accessRequestId,
                    "RDP session closed");
            rdpAuditLogRepository.save(new RdpAuditLog(accessRequestId,
                    session.requesterUsername(), session.tenantId(),
                    session.resourceName(), RdpAuditLog.EventType.SESSION_END, "RDP session closed"));
        } catch (Exception e) {
            log.warn("Error closing RDP session {}: {}", accessRequestId, e.getMessage());
        }
    }

    public boolean hasActiveSession(Long accessRequestId) {
        return activeSessions.containsKey(accessRequestId);
    }

    public SessionInfo getSessionInfo(Long requestId) {
        ActiveRdpSession s = activeSessions.get(requestId);
        if (s == null) return null;
        return new SessionInfo(s.requesterUsername(), s.tenantId(), s.resourceName());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void relayToWs(GuacamoleTunnel tunnel, WebSocketSession wsSession, Long requestId) {
        try {
            while (wsSession.isOpen() && tunnel.isOpen()) {
                char[] chars = tunnel.acquireReader().read();
                tunnel.releaseReader();
                if (chars == null) break;
                String instruction = new String(chars);
                // Clipboard copy: server sends clipboard data to browser (user copied from remote desktop)
                // "clipboard" = 9 chars → opcode prefix is "9.clipboard,"
                if (instruction.startsWith("9.clipboard,")) {
                    ActiveRdpSession s = activeSessions.get(requestId);
                    if (s != null) {
                        try {
                            rdpAuditLogRepository.save(new RdpAuditLog(requestId,
                                    s.requesterUsername(), s.tenantId(), s.resourceName(),
                                    RdpAuditLog.EventType.CLIPBOARD_COPY, "Clipboard copy from remote desktop"));
                        } catch (Exception ignored) {}
                    }
                }
                synchronized (wsSession) {
                    if (wsSession.isOpen()) {
                        wsSession.sendMessage(new TextMessage(instruction));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Guacamole relay ended for session {}: {}", requestId, e.getMessage());
        } finally {
            closeSession(requestId);
            try { wsSession.close(); } catch (IOException ignored) {}
        }
    }

    @PreDestroy
    public void shutdown() {
        activeSessions.keySet().forEach(this::closeSession);
    }

    // ── Public records ────────────────────────────────────────────────────────

    public record SessionInfo(String username, String tenantId, String resourceName) {}

    // ── Inner record ──────────────────────────────────────────────────────────

    private record ActiveRdpSession(
            GuacamoleTunnel tunnel,
            WebSocketSession wsSession,
            String requesterUsername,
            String tenantId,
            String resourceName) {}
}
