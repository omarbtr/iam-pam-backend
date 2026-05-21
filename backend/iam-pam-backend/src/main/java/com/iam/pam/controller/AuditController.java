package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.AuditLogDTO;
import com.iam.pam.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Audit Logs", description = "Consultation des logs d'audit pour conformité (auditor, tenant-admin)")
@RestController
@RequestMapping("/api/auditor")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "Consulter les logs d'audit paginés avec filtres optionnels")
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('auditor', 'tenant-admin')")
    public ResponseEntity<ApiResponse<Page<AuditLogDTO.Response>>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        boolean hasFilters = (username != null && !username.isBlank())
                || (action != null && !action.isBlank())
                || (dateFrom != null && !dateFrom.isBlank())
                || (dateTo != null && !dateTo.isBlank());

        Page<AuditLogDTO.Response> result = hasFilters
                ? auditService.getFilteredLogs(page, size, username, action, dateFrom, dateTo)
                : auditService.getLogs(page, size);

        return ResponseEntity.ok(ApiResponse.success(result, "Logs retrieved"));
    }

    @Operation(summary = "Statistiques du tableau de bord (sessions actives + sessions/jour)")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('auditor', 'tenant-admin')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(auditService.getStats(), "Stats retrieved"));
    }

    @Operation(summary = "Utilisation des ressources — nombre de sessions par ressource")
    @GetMapping("/resource-usage")
    @PreAuthorize("hasAnyRole('auditor', 'tenant-admin')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getResourceUsage() {
        return ResponseEntity.ok(ApiResponse.success(auditService.getResourceUsage(), "Resource usage retrieved"));
    }

    @Operation(summary = "Liste des sessions démarrées un jour donné (YYYY-MM-DD)")
    @GetMapping("/sessions-by-day")
    @PreAuthorize("hasAnyRole('auditor', 'tenant-admin')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSessionsByDay(
            @RequestParam String date) {
        return ResponseEntity.ok(ApiResponse.success(auditService.getSessionsForDay(date), "Sessions retrieved"));
    }

    @Operation(summary = "Consulter les logs d'un utilisateur (auditor, tenant-admin)")
    @GetMapping("/logs/user/{username}")
    @PreAuthorize("hasAnyRole('auditor', 'tenant-admin')")
    public ResponseEntity<ApiResponse<List<AuditLogDTO.Response>>> getUserLogs(
            @PathVariable String username) {

        return ResponseEntity.ok(
                ApiResponse.success(auditService.getUserLogs(username), "User logs retrieved")
        );
    }

    @Operation(summary = "Exporter tous les logs d'audit en CSV (auditor, tenant-admin)")
    @GetMapping("/logs/export")
    @PreAuthorize("hasAnyRole('auditor', 'tenant-admin')")
    public ResponseEntity<byte[]> exportLogs() {
        byte[] bytes = auditService.exportLogsAsCsv().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}






