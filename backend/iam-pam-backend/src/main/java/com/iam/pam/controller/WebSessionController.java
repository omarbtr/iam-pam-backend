package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.entity.WebAuditLog;
import com.iam.pam.repository.WebAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Web Session Audit", description = "Logs d'audit des sessions web proxy")
@RestController
@RequestMapping("/api/pam/web-sessions")
public class WebSessionController {

    private final WebAuditLogRepository webAuditLogRepository;

    public WebSessionController(WebAuditLogRepository webAuditLogRepository) {
        this.webAuditLogRepository = webAuditLogRepository;
    }

    @Operation(summary = "Obtenir les logs d'audit d'une session web")
    @GetMapping("/{requestId}/audit-logs")
    @PreAuthorize("hasAnyRole('tenant-admin', 'admin')")
    public ResponseEntity<ApiResponse<List<WebAuditLog>>> getAuditLogs(@PathVariable Long requestId) {
        List<WebAuditLog> logs = webAuditLogRepository.findByAccessRequestIdOrderByOccurredAtAsc(requestId);
        return ResponseEntity.ok(ApiResponse.success(logs, "Web audit logs retrieved"));
    }
}
