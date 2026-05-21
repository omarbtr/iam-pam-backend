package com.iam.pam.websocket;

import com.iam.pam.entity.RdpAuditLog;
import com.iam.pam.repository.RdpAuditLogRepository;
import com.iam.pam.service.GuacamoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket handler for browser-based RDP sessions via Apache Guacamole.
 * URL pattern: /ws/rdp/{requestId}
 *
 * Messages from client: Guacamole protocol instructions (mouse, key, size events)
 * Messages to client:   Guacamole protocol instructions (display rendering)
 */
@Component
public class GuacamoleWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GuacamoleWebSocketHandler.class);

    private final GuacamoleService guacamoleService;
    private final RdpAuditLogRepository rdpAuditLogRepository;
    private final JwtDecoder jwtDecoder;

    // X11 keysyms for special keys
    private static final int KEYSYM_RETURN    = 0xff0d; // Enter
    private static final int KEYSYM_BACKSPACE = 0xff08;
    private static final int KEYSYM_ESCAPE    = 0xff1b;
    private static final int KEYSYM_TAB       = 0xff09;

    private final Map<String, Long>        sessionRequestMap = new ConcurrentHashMap<>();
    private final Map<Long, StringBuilder> keyBuffers        = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> lastMouseMask     = new ConcurrentHashMap<>();
    // Tracks which modifier keys are currently held per session (Ctrl/Alt/Shift)
    private final Map<Long, Set<Integer>>  heldModifiers     = new ConcurrentHashMap<>();

    public GuacamoleWebSocketHandler(GuacamoleService guacamoleService,
                                     RdpAuditLogRepository rdpAuditLogRepository,
                                     JwtDecoder jwtDecoder) {
        this.guacamoleService = guacamoleService;
        this.rdpAuditLogRepository = rdpAuditLogRepository;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = UriComponentsBuilder.fromUri(session.getUri())
                .build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            log.warn("RDP WebSocket rejected — missing token");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("RDP WebSocket rejected — invalid token: {}", e.getMessage());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Long requestId = extractRequestId(session);
        if (requestId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessionRequestMap.put(session.getId(), requestId);
        keyBuffers.put(requestId, new StringBuilder());
        lastMouseMask.put(requestId, new AtomicInteger(0));
        heldModifiers.put(requestId, ConcurrentHashMap.newKeySet());
        log.info("RDP WebSocket connected for request {}", requestId);

        // Run guacd setup in background — ConfiguredGuacamoleSocket blocks for ~800ms
        // and must NOT block the Tomcat WebSocket thread (causes null transport error).
        Thread setup = new Thread(() -> {
            try {
                guacamoleService.openSession(requestId, session);
            } catch (Exception e) {
                log.error("Failed to open RDP session {}: {}", requestId, e.getMessage());
                sessionRequestMap.remove(session.getId());
                try { if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR); }
                catch (IOException ignored) {}
            }
        });
        setup.setDaemon(true);
        setup.setName("guac-setup-" + requestId);
        setup.start();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long requestId = sessionRequestMap.get(session.getId());
        if (requestId == null) return;
        String payload = message.getPayload();
        guacamoleService.sendInstruction(requestId, payload);
        auditGuacamoleInstruction(requestId, payload);
    }

    private void auditGuacamoleInstruction(Long requestId, String instruction) {
        try {
            GuacamoleService.SessionInfo info = guacamoleService.getSessionInfo(requestId);
            String username     = info != null ? info.username()     : "unknown";
            String tenantId     = info != null ? info.tenantId()     : "unknown";
            String resourceName = info != null ? info.resourceName() : null;

            // "clipboard" = 9 chars → prefix is "9.clipboard," (NOT "5.clipboard," which was a bug)
        if (instruction.startsWith("9.clipboard,")) {
            log.debug("RDP audit: CLIPBOARD_PASTE for request {}", requestId);
                rdpAuditLogRepository.save(new RdpAuditLog(requestId, username, tenantId,
                        resourceName, RdpAuditLog.EventType.CLIPBOARD_PASTE,
                        "Clipboard paste to remote desktop"));

            } else if (instruction.startsWith("3.key,")) {
                handleKeyInstruction(requestId, instruction, username, tenantId, resourceName);

            } else if (instruction.startsWith("5.mouse,")) {
                handleMouseInstruction(requestId, instruction, username, tenantId, resourceName);

            } else if (instruction.startsWith("4.file,") || instruction.startsWith("3.file,")) {
                rdpAuditLogRepository.save(new RdpAuditLog(requestId, username, tenantId,
                        resourceName, RdpAuditLog.EventType.FILE_TRANSFER,
                        "File transfer: " + instruction.substring(0, Math.min(120, instruction.length())).trim()));
            }
        } catch (Exception e) {
            log.debug("RDP audit log error: {}", e.getMessage());
        }
    }

    /**
     * Parses a Guacamole key instruction (3.key,<len>.<keysym>,<len>.<pressed>;).
     *
     * - Tracks Ctrl/Alt/Shift modifier state per session.
     * - When Ctrl is held + printable char → logs KEY_COMBO (was previously unreachable due to early return bug).
     * - When no modifier → buffers printable chars; flushes to DB as TEXT_INPUT on Enter.
     * - Backspace edits the buffer; Escape clears it.
     */
    private void handleKeyInstruction(Long requestId, String instruction,
                                       String username, String tenantId, String resourceName) {
        // Format: 3.key,<len>.<keysym>,<len>.<pressed>;
        String body = instruction.replaceAll(";$", "");
        String[] parts = body.split(",");
        if (parts.length < 3) return;

        int keysym, pressed;
        try {
            keysym  = Integer.parseInt(parts[1].substring(parts[1].indexOf('.') + 1));
            pressed = Integer.parseInt(parts[2].substring(parts[2].indexOf('.') + 1));
        } catch (NumberFormatException e) { return; }

        // Track modifier keys on BOTH key-down and key-up so we know their state accurately
        // Ctrl L=65507/R=65508, Alt L=65513/R=65514, Shift L=65505/R=65506
        if (keysym == 65507 || keysym == 65508
         || keysym == 65513 || keysym == 65514
         || keysym == 65505 || keysym == 65506) {
            Set<Integer> mods = heldModifiers.computeIfAbsent(requestId, k -> ConcurrentHashMap.newKeySet());
            if (pressed == 1) mods.add(keysym);
            else              mods.remove(keysym);
            return;
        }

        if (pressed != 1) return; // only process key-down events from here on

        boolean ctrlHeld = heldModifiers.getOrDefault(requestId, Collections.emptySet())
                .stream().anyMatch(k -> k == 65507 || k == 65508);

        // Printable ASCII (space 0x20 to tilde 0x7e)
        if (keysym >= 0x20 && keysym <= 0x7e) {
            if (ctrlHeld) {
                // e.g. Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+Z, Ctrl+A, Ctrl+S ...
                String letter = String.valueOf((char) keysym).toUpperCase();
                log.debug("RDP audit: KEY_COMBO Ctrl+{} for request {}", letter, requestId);
                rdpAuditLogRepository.save(new RdpAuditLog(requestId, username, tenantId,
                        resourceName, RdpAuditLog.EventType.KEY_COMBO, "Ctrl+" + letter));
            } else {
                keyBuffers.computeIfAbsent(requestId, k -> new StringBuilder()).append((char) keysym);
            }
            return;
        }

        // Enter → flush buffered text
        if (keysym == KEYSYM_RETURN) {
            flushKeyBuffer(requestId, username, tenantId, resourceName);
            return;
        }
        // Escape → discard buffer
        if (keysym == KEYSYM_ESCAPE) {
            StringBuilder buf = keyBuffers.get(requestId);
            if (buf != null) buf.setLength(0);
            return;
        }
        // Backspace → edit buffer
        if (keysym == KEYSYM_BACKSPACE) {
            StringBuilder buf = keyBuffers.get(requestId);
            if (buf != null && buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
            return;
        }
        // Tab → record as space in buffer (e.g. command-line tab-completion)
        if (keysym == KEYSYM_TAB) {
            keyBuffers.computeIfAbsent(requestId, k -> new StringBuilder()).append(' ');
        }
    }

    /**
     * Parses a Guacamole mouse instruction (5.mouse,<len>.<x>,<len>.<y>,<len>.<mask>;).
     * Logs left/right mouse clicks by detecting button-down transitions.
     */
    private void handleMouseInstruction(Long requestId, String instruction,
                                         String username, String tenantId, String resourceName) {
        // Format: 5.mouse,<len>.<x>,<len>.<y>,<len>.<mask>;
        String body = instruction.replaceAll(";$", "");
        String[] parts = body.split(",");
        if (parts.length < 4) return;

        int x, y, mask;
        try {
            x    = Integer.parseInt(parts[1].substring(parts[1].indexOf('.') + 1));
            y    = Integer.parseInt(parts[2].substring(parts[2].indexOf('.') + 1));
            mask = Integer.parseInt(parts[3].substring(parts[3].indexOf('.') + 1));
        } catch (NumberFormatException e) { return; }

        AtomicInteger prevMaskHolder = lastMouseMask.computeIfAbsent(requestId, k -> new AtomicInteger(0));
        int prev = prevMaskHolder.getAndSet(mask);

        // Detect button-DOWN transitions (bit was 0, now 1)
        // Only log right-clicks (bit 2, value 4) — left-clicks are too frequent (every scroll, select, navigate)
        int newButtons = mask & ~prev;
        if ((newButtons & 4) != 0) {
            log.debug("RDP audit: RIGHT_CLICK at ({},{}) for request {}", x, y, requestId);
            rdpAuditLogRepository.save(new RdpAuditLog(requestId, username, tenantId,
                    resourceName, RdpAuditLog.EventType.MOUSE_CLICK, "Right click at (" + x + "," + y + ")"));
        }
    }

    private void flushKeyBuffer(Long requestId) {
        flushKeyBuffer(requestId, "unknown", "unknown", null);
    }

    private void flushKeyBuffer(Long requestId, String username, String tenantId, String resourceName) {
        StringBuilder buf = keyBuffers.get(requestId);
        if (buf == null || buf.length() == 0) return;
        String text = buf.toString().trim();
        buf.setLength(0);
        if (text.isEmpty()) return;
        try {
            rdpAuditLogRepository.save(new RdpAuditLog(requestId, username, tenantId,
                    resourceName, RdpAuditLog.EventType.TEXT_INPUT, text));
        } catch (Exception e) {
            log.debug("RDP text input audit error: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long requestId = sessionRequestMap.remove(session.getId());
        if (requestId != null) {
            flushKeyBuffer(requestId); // save any remaining typed text
            keyBuffers.remove(requestId);
            lastMouseMask.remove(requestId);
            heldModifiers.remove(requestId);
            guacamoleService.closeSession(requestId);
            log.info("RDP WebSocket closed for request {} ({})", requestId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("RDP WebSocket transport error [{}]: {}",
                ex != null ? ex.getClass().getSimpleName() : "null",
                ex != null ? ex.getMessage() : "(null exception)");
        Long requestId = sessionRequestMap.remove(session.getId());
        if (requestId != null) guacamoleService.closeSession(requestId);
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
