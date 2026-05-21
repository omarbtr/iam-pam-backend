package com.iam.pam.controller;

import com.iam.pam.dto.AccessRequestDTO;
import com.iam.pam.dto.ApiResponse;
import com.iam.pam.service.AccessRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PAM Access Requests", description = "Cycle de vie des demandes d'accès PAM (user/pam-access pour créer, tenant-admin pour approuver)")
@RestController
@RequestMapping("/api/pam/requests")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    @Operation(summary = "Toutes les demandes d'accès du tenant (tenant-admin)")
    @GetMapping
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getAllRequests(), "Requests retrieved")
        );
    }

    @Operation(summary = "Demandes en attente d'approbation (tenant-admin)")
    @GetMapping("/pending")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getPending() {
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getPendingRequests(), "Pending requests retrieved")
        );
    }

    @Operation(summary = "Mes demandes d'accès (user, pam-access, tenant-admin)")
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getMyRequests(
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getMyRequests(username), "Your requests retrieved")
        );
    }

    @Operation(summary = "Créer une demande d'accès PAM (user, pam-access, tenant-admin)")
    @PostMapping
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> create(
            @Valid @RequestBody AccessRequestDTO.Request dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(accessRequestService.createRequest(dto, username), "Request created")
        );
    }

    @Operation(summary = "Approuver ou rejeter une demande d'accès (tenant-admin)")
    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> review(
            @PathVariable Long id,
            @Valid @RequestBody AccessRequestDTO.ReviewRequest dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.reviewRequest(id, dto, username), "Request reviewed")
        );
    }

    @Operation(summary = "Révoquer un accès approuvé (tenant-admin)")
    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> revoke(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.revokeRequest(id, username), "Access revoked")
        );
    }

    @Operation(summary = "Sessions PAM actives de l'utilisateur connecté (user, pam-access, tenant-admin)")
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getActiveSessions(
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getActiveSessions(username), "Active sessions retrieved")
        );
    }

    @Operation(summary = "Temps restant pour une session PAM (user, pam-access, tenant-admin)")
    @GetMapping("/{id}/remaining-time")
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> getRemainingTime(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getRemainingTime(id, username), "Remaining time retrieved")
        );
    }

    @Operation(summary = "Terminer sa propre session PAM (user, pam-access, tenant-admin)")
    @PutMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> terminate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.revokeOwnSession(id, username), "Session terminated")
        );
    }
}