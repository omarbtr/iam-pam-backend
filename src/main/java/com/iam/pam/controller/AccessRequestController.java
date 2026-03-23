package com.iam.pam.controller;

import com.iam.pam.dto.AccessRequestDTO;
import com.iam.pam.dto.ApiResponse;
import com.iam.pam.service.AccessRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pam/requests")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    // GET /api/pam/requests - Toutes les demandes du tenant (admin)
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getAllRequests(), "Requests retrieved")
        );
    }

    // GET /api/pam/requests/pending - Demandes en attente (admin)
    @GetMapping("/pending")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getPending() {
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getPendingRequests(), "Pending requests retrieved")
        );
    }

    // GET /api/pam/requests/mine - Mes demandes
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'admin')")
    public ResponseEntity<ApiResponse<List<AccessRequestDTO.Response>>> getMyRequests(
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.getMyRequests(username), "Your requests retrieved")
        );
    }

    // POST /api/pam/requests - Creer une demande
    @PostMapping
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> create(
            @Valid @RequestBody AccessRequestDTO.Request dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(accessRequestService.createRequest(dto, username), "Request created")
        );
    }

    // PUT /api/pam/requests/{id}/review - Approuver/Rejeter (admin)
    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> review(
            @PathVariable Long id,
            @Valid @RequestBody AccessRequestDTO.ReviewRequest dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.reviewRequest(id, dto, username), "Request reviewed")
        );
    }

    // PUT /api/pam/requests/{id}/revoke - Revoquer un acces (admin)
    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<AccessRequestDTO.Response>> revoke(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(accessRequestService.revokeRequest(id, username), "Access revoked")
        );
    }
}