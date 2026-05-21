package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.RdpAuditLog;
import com.iam.pam.repository.AccessRequestRepository;
import com.iam.pam.repository.RdpAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@Tag(name = "RDP Session Audit", description = "Enregistrements vidéo et logs d'audit RDP")
@RestController
@RequestMapping("/api/pam/rdp-sessions")
public class RdpSessionController {

    private static final Logger log = LoggerFactory.getLogger(RdpSessionController.class);

    private final AccessRequestRepository accessRequestRepository;
    private final RdpAuditLogRepository rdpAuditLogRepository;

    public RdpSessionController(AccessRequestRepository accessRequestRepository,
                                RdpAuditLogRepository rdpAuditLogRepository) {
        this.accessRequestRepository = accessRequestRepository;
        this.rdpAuditLogRepository = rdpAuditLogRepository;
    }

    @Operation(summary = "Obtenir les logs d'audit d'une session RDP")
    @GetMapping("/{requestId}/audit-logs")
    @PreAuthorize("hasAnyRole('tenant-admin', 'admin')")
    public ResponseEntity<ApiResponse<List<RdpAuditLog>>> getAuditLogs(@PathVariable Long requestId) {
        List<RdpAuditLog> logs = rdpAuditLogRepository.findByAccessRequestIdOrderByOccurredAtAsc(requestId);
        return ResponseEntity.ok(ApiResponse.success(logs, "RDP audit logs retrieved"));
    }

    @Operation(summary = "Télécharger l'enregistrement vidéo d'une session RDP")
    @GetMapping("/{requestId}/recording")
    @PreAuthorize("hasAnyRole('tenant-admin', 'admin')")
    public ResponseEntity<Resource> getRecording(@PathVariable Long requestId) {
        AccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

        String recordingPath = request.getRecordingPath();
        if (recordingPath == null || recordingPath.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        File file = new File(recordingPath);
        if (!file.exists() || !file.isFile()) {
            log.warn("Recording file not found: {}", recordingPath);
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rdp-recording-" + requestId + ".guac\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
