package com.iam.pam.service;

import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.Resource;
import com.iam.pam.repository.AccessRequestRepository;
import jakarta.annotation.PreDestroy;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages SSH sessions through the bastion VM.
 * Flow: Browser <-WebSocket-> Spring Boot <-SSH-> Bastion VM <-SSH-> Target Resource
 */
@Service
public class BastionService {

    private static final Logger log = LoggerFactory.getLogger(BastionService.class);

    @Value("${bastion.host}")
    private String bastionHost;

    @Value("${bastion.port:22}")
    private int bastionPort;

    @Value("${bastion.user:bastion-agent}")
    private String bastionUser;

    @Value("${bastion.private-key-path}")
    private String privateKeyPath;

    private final AccessRequestRepository accessRequestRepository;
    private final AuditService auditService;
    private final ResourceLoader resourceLoader;
    private final AccessRequestService accessRequestService;

    // Active sessions indexed by AccessRequest ID
    private final Map<Long, ActiveSession> activeSessions = new ConcurrentHashMap<>();

    private final SshClient sshClient;

    public BastionService(AccessRequestRepository accessRequestRepository,
                          AuditService auditService,
                          ResourceLoader resourceLoader,
                          AccessRequestService accessRequestService) {
        this.accessRequestRepository = accessRequestRepository;
        this.auditService = auditService;
        this.resourceLoader = resourceLoader;
        this.accessRequestService = accessRequestService;
        this.sshClient = SshClient.setUpDefaultClient();
        this.sshClient.start();
        log.info("SSH client started");
    }

    /**
     * Opens an SSH session through the bastion to the target resource
     * and relays data between the WebSocket and the SSH shell.
     */
    public void openSession(Long accessRequestId, WebSocketSession wsSession) throws Exception {
        AccessRequest request = accessRequestRepository.findByIdWithResource(accessRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + accessRequestId));

        if (!request.isActive()) {
            wsSession.sendMessage(new TextMessage("\r\n[IAM-PAM] Session expired or not approved.\r\n"));
            wsSession.close();
            return;
        }

        Resource resource = request.getResource();
        String targetHost  = resource.getHost();
        int    targetPort  = resource.getPort() != null ? resource.getPort() : 22;
        String targetUser  = resource.getCredentialUsername() != null ? resource.getCredentialUsername() : "ubuntu";
        String targetPass  = resource.getCredentialPassword();
        // Capture before leaving Hibernate session to avoid lazy-load later
        String resourceName      = resource.getName();
        String requesterUsername = request.getRequesterUsername();
        String tenantId          = request.getTenantId();

        log.info("Opening bastion session for request {} to {}@{}:{}", accessRequestId, targetUser, targetHost, targetPort);

        // Connect to bastion VM
        ClientSession bastionSession = sshClient
                .connect(bastionUser, bastionHost, bastionPort)
                .verify(10, TimeUnit.SECONDS)
                .getSession();

        bastionSession.addPublicKeyIdentity(loadKeyPair());
        bastionSession.auth().verify(15, TimeUnit.SECONDS);

        // Open PTY shell on bastion
        ChannelShell shell = bastionSession.createShellChannel();
        shell.setUsePty(true);
        shell.setPtyType("xterm-256color");

        PipedOutputStream toShell      = new PipedOutputStream();
        PipedInputStream  fromShellIn  = new PipedInputStream(toShell);
        PipedInputStream  shellOutput  = new PipedInputStream();
        PipedOutputStream toWs         = new PipedOutputStream(shellOutput);

        shell.setIn(fromShellIn);
        shell.setOut(toWs);
        shell.setErr(toWs);
        shell.open().verify(10, TimeUnit.SECONDS);

        // From bastion, SSH to target resource
        String sshCmd = buildSshCommand(targetUser, targetHost, targetPort, targetPass);
        toShell.write((sshCmd + "\n").getBytes(StandardCharsets.UTF_8));
        toShell.flush();

        accessRequestService.recordFirstAccess(accessRequestId);
        auditService.log(AuditLog.AuditAction.SESSION_STARTED,
                requesterUsername, tenantId, resourceName, accessRequestId,
                "Bastion session opened to " + targetHost);

        ActiveSession session = new ActiveSession(bastionSession, shell, toShell, toWs, wsSession,
                requesterUsername, tenantId, resourceName);
        activeSessions.put(accessRequestId, session);

        // Relay output from SSH shell to WebSocket (daemon thread — non-blocking)
        Thread relay = new Thread(() -> relayOutputToWs(shellOutput, wsSession, accessRequestId));
        relay.setDaemon(true);
        relay.setName("bastion-relay-" + accessRequestId);
        relay.start();
    }

    /**
     * Sends keystrokes from the browser terminal to the SSH shell.
     */
    public void sendInput(Long accessRequestId, String input) {
        ActiveSession session = activeSessions.get(accessRequestId);
        if (session == null) return;
        try {
            session.toShell().write(input.getBytes(StandardCharsets.UTF_8));
            session.toShell().flush();
        } catch (IOException e) {
            log.debug("Input pipe broken for session {}: {}", accessRequestId, e.getMessage());
            return;
        }
        String cleaned = input.replaceAll("[\\x00-\\x1F\\x7F]", "").trim();
        if (!cleaned.isEmpty()) {
            try {
                auditService.log(AuditLog.AuditAction.COMMAND_EXECUTED,
                        session.requesterUsername(), session.tenantId(),
                        session.resourceName(), accessRequestId,
                        "CMD: " + cleaned);
            } catch (Exception e) {
                log.warn("Audit log failed for session {}: {}", accessRequestId, e.getMessage());
            }
        }
    }

    /**
     * Terminates a bastion session (on revocation or WebSocket disconnect).
     */
    public void closeSession(Long accessRequestId) {
        ActiveSession session = activeSessions.remove(accessRequestId);
        if (session == null) return;
        try {
            session.shell().close(true);
            session.bastionSession().close(true);
            log.info("Bastion session {} closed", accessRequestId);

            auditService.log(AuditLog.AuditAction.SESSION_ENDED,
                    session.requesterUsername(), session.tenantId(),
                    session.resourceName(), accessRequestId,
                    "Bastion session closed");
        } catch (Exception e) {
            log.warn("Error closing bastion session {}: {}", accessRequestId, e.getMessage());
        }
    }

    public boolean hasActiveSession(Long accessRequestId) {
        return activeSessions.containsKey(accessRequestId);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void relayOutputToWs(InputStream shellOutput, WebSocketSession wsSession,
                                  Long requestId) {
        byte[] buf = new byte[4096];
        try {
            int n;
            while (wsSession.isOpen() && (n = shellOutput.read(buf)) != -1) {
                wsSession.sendMessage(new TextMessage(new String(buf, 0, n, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            log.debug("SSH relay ended for session {}: {}", requestId, e.getMessage());
        } finally {
            closeSession(requestId);
            try { wsSession.close(); } catch (IOException ignored) {}
        }
    }

    private String buildSshCommand(String user, String host, int port, String password) {
        if (password != null && !password.isBlank()) {
            return String.format("sshpass -p '%s' ssh -o StrictHostKeyChecking=no -p %d %s@%s",
                    password.replace("'", "'\\''"), port, user, host);
        }
        return String.format("ssh -o StrictHostKeyChecking=no -p %d %s@%s", port, user, host);
    }

    private KeyPair loadKeyPair() throws Exception {
        org.springframework.core.io.Resource keyResource = resourceLoader.getResource(privateKeyPath);
        Path keyPath = keyResource.getFile().toPath();
        FileKeyPairProvider provider = new FileKeyPairProvider(keyPath);
        Iterable<KeyPair> pairs = provider.loadKeys(null);
        java.util.Iterator<KeyPair> it = pairs.iterator();
        if (!it.hasNext()) throw new IllegalStateException("No key pair loaded from " + privateKeyPath);
        return it.next();
    }

    @PreDestroy
    public void shutdown() {
        activeSessions.keySet().forEach(this::closeSession);
        if (sshClient != null) sshClient.stop();
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    private record ActiveSession(
            ClientSession bastionSession,
            ChannelShell shell,
            OutputStream toShell,
            OutputStream toWs,
            WebSocketSession wsSession,
            String requesterUsername,
            String tenantId,
            String resourceName) {}
}
