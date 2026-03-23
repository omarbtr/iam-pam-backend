package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.AuditLogDTO;
import com.iam.pam.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auditor")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    // GET /api/auditor/logs?page=0&size=20
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('auditor', 'admin')")
    public ResponseEntity<ApiResponse<Page<AuditLogDTO.Response>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                ApiResponse.success(auditService.getLogs(page, size), "Logs retrieved")
        );
    }

    // GET /api/auditor/logs/user/{username}
    @GetMapping("/logs/user/{username}")
    @PreAuthorize("hasAnyRole('auditor', 'admin')")
    public ResponseEntity<ApiResponse<List<AuditLogDTO.Response>>> getUserLogs(
            @PathVariable String username) {

        return ResponseEntity.ok(
                ApiResponse.success(auditService.getUserLogs(username), "User logs retrieved")
        );
    }
}






