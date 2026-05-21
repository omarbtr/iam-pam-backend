package com.iam.pam.controller;

import com.iam.pam.service.DbTunnelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class DbSessionController {

    private static final Logger log = LoggerFactory.getLogger(DbSessionController.class);

    private final DbTunnelService dbTunnelService;

    public DbSessionController(DbTunnelService dbTunnelService) {
        this.dbTunnelService = dbTunnelService;
    }

    /**
     * Starts a DB tunnel session for an approved access request.
     * Returns sessionId + dbType so the frontend can show the right icon.
     */
    @PostMapping("/api/pam/db/start/{requestId}")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin', 'pam-access')")
    public ResponseEntity<Map<String, String>> startSession(@PathVariable Long requestId) {
        try {
            DbTunnelService.DbSessionInfo info = dbTunnelService.startSession(requestId);
            return ResponseEntity.ok(Map.of(
                    "sessionId", info.sessionId(),
                    "dbType",    info.dbType(),
                    "dbName",    info.dbName()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("DB session start rejected for request {}: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("DB session start rejected for request {}: {}", requestId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("DB session start failed for request {}: {}", requestId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Impossible d'ouvrir le tunnel DB: " + e.getMessage()));
        }
    }

    /**
     * Executes a SQL query through the tunnelled JDBC connection.
     * Body: { "sql": "SELECT ..." }
     */
    @PostMapping("/api/pam/db/query/{sessionId}")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin', 'pam-access')")
    public ResponseEntity<Map<String, Object>> executeQuery(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> body) {
        String sql = body.getOrDefault("sql", "").trim();
        if (sql.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "SQL query is empty"));
        }
        try {
            Map<String, Object> result = dbTunnelService.executeQuery(sessionId, sql);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Query error on session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns the schema tree (schemas → tables → columns) for the connected DB.
     */
    @GetMapping("/api/pam/db/schema/{sessionId}")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin', 'pam-access')")
    public ResponseEntity<List<Map<String, Object>>> fetchSchema(@PathVariable String sessionId) {
        try {
            return ResponseEntity.ok(dbTunnelService.fetchSchema(sessionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        } catch (Exception e) {
            log.warn("Schema fetch error on session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    /**
     * Terminates the DB tunnel session and closes the SSH port forward.
     */
    @DeleteMapping("/api/pam/db/stop/{sessionId}")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin', 'pam-access')")
    public ResponseEntity<Void> stopSession(@PathVariable String sessionId) {
        dbTunnelService.endSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
