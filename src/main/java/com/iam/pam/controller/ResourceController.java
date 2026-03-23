package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.ResourceDTO;
import com.iam.pam.service.ResourceService;
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
@RequestMapping("/api/pam/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    // GET /api/pam/resources
    @GetMapping
    @PreAuthorize("hasAnyRole('pam-access', 'admin')")
    public ResponseEntity<ApiResponse<List<ResourceDTO.Response>>> getAll() {
        return ResponseEntity.ok(
                ApiResponse.success(resourceService.getAllResources(), "Resources retrieved")
        );
    }

    // GET /api/pam/resources/{id}
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('pam-access', 'admin')")
    public ResponseEntity<ApiResponse<ResourceDTO.Response>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success(resourceService.getById(id))
        );
    }

    // POST /api/pam/resources
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<ResourceDTO.Response>> create(
            @Valid @RequestBody ResourceDTO.Request dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(resourceService.create(dto, username), "Resource created")
        );
    }

    // PUT /api/pam/resources/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<ResourceDTO.Response>> update(
            @PathVariable Long id,
            @Valid @RequestBody ResourceDTO.Request dto,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        return ResponseEntity.ok(
                ApiResponse.success(resourceService.update(id, dto, username), "Resource updated")
        );
    }

    // DELETE /api/pam/resources/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        String username = jwt.getClaimAsString("preferred_username");
        resourceService.delete(id, username);
        return ResponseEntity.ok(ApiResponse.success(null, "Resource deactivated"));
    }
}