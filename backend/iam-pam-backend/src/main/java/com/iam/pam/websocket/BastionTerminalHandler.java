package com.iam.pam.websocket;

import com.iam.pam.service.BastionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for the browser terminal.
 * URL pattern: /ws/session/{requestId}
 *
 * Messages from client:
 *   - Text frames: raw keystrokes forwarded to SSH shell
 *   - "RESIZE:cols:rows" — terminal resize (future use)
 *
 * Messages to client:
 *   - Text frames: raw SSH output (xterm.js renders ANSI sequences)
 */
@Component
public class BastionTerminalHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BastionTerminalHandler.class);

    private final BastionService bastionService;
    private final JwtDecoder jwtDecoder;

    // Map wsSession.getId() -> accessRequestId
    private final Map<String, Long> sessionRequestMap = new ConcurrentHashMap<>();

    public BastionTerminalHandler(BastionService bastionService, JwtDecoder jwtDecoder) {
        this.bastionService = bastionService;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Validate JWT passed as ?token= query parameter
        String token = UriComponentsBuilder.fromUri(session.getUri())
                .build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            log.warn("WebSocket rejected — missing token for URI {}", session.getUri());
            session.sendMessage(new TextMessage("[IAM-PAM] Non authentifié.\r\n"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("WebSocket rejected — invalid token: {}", e.getMessage());
            session.sendMessage(new TextMessage("[IAM-PAM] Token invalide.\r\n"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Long requestId = extractRequestId(session);
        if (requestId == null) {
            session.sendMessage(new TextMessage("[IAM-PAM] Invalid session URL.\r\n"));
            session.close();
            return;
        }
        sessionRequestMap.put(session.getId(), requestId);
        log.info("WebSocket connected for request {}", requestId);

        try {
            bastionService.openSession(requestId, session);
        } catch (Exception e) {
            log.error("Failed to open bastion session for request {}: {}", requestId, e.getMessage());
            sessionRequestMap.remove(session.getId());
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("\r\n[31m[IAM-PAM] Erreur de connexion SSH: " + e.getMessage() + "[0m\r\n"));
                session.close(CloseStatus.SERVER_ERROR);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long requestId = sessionRequestMap.get(session.getId());
        if (requestId == null) return;

        String payload = message.getPayload();
        if (payload.startsWith("RESIZE:")) {
            return;
        }
        bastionService.sendInput(requestId, payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long requestId = sessionRequestMap.remove(session.getId());
        if (requestId != null) {
            bastionService.closeSession(requestId);
            log.info("WebSocket closed for request {} ({})", requestId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("WebSocket transport error: {}", ex.getMessage());
        Long requestId = sessionRequestMap.remove(session.getId());
        if (requestId != null) bastionService.closeSession(requestId);
    }

    private Long extractRequestId(WebSocketSession session) {
        try {
            String path = session.getUri().getPath();
            String[] parts = path.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }
}
