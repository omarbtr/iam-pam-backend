package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.ResourceDTO;
import com.iam.pam.service.ResourceService;
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

@Tag(name = "PAM Resources", description = "Gestion des ressources PAM — SSH, RDP, DB, Web, API (tenant-admin, pam-access)")
@RestController
@RequestMapping("/api/pam/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @Operation(summary = "Lister toutes les ressources PAM (pam-access, tenant-admin)")
    @GetMapping
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<List<ResourceDTO.Response>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(resourceService.getAllResources(), "Resources retrieved")
        );
    }

    @Operation(summary = "Obtenir une ressource PAM par ID (user, pam-access, tenant-admin)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('user', 'pam-access', 'tenant-admin')")
    public ResponseEntity<ApiResponse<ResourceDTO.Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(resourceService.getById(id))
        );
    }

    @Operation(summary = "Créer une ressource PAM (tenant-admin)")
    @PostMapping
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<ResourceDTO.Response>> create(
            @Valid @RequestBody ResourceDTO.Request dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(resourceService.create(dto, username), "Resource created")
        );
    }

    @Operation(summary = "Mettre à jour une ressource PAM (tenant-admin)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<ResourceDTO.Response>> update(
            @PathVariable Long id,
            @Valid @RequestBody ResourceDTO.Request dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(resourceService.update(id, dto, username), "Resource updated")
        );
    }

    @Operation(summary = "Désactiver une ressource PAM (tenant-admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        resourceService.delete(id, username);
        return ResponseEntity.ok(ApiResponse.success(null, "Resource deactivated"));
    }
}