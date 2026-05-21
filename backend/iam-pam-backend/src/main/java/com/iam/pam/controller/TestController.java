package com.iam.pam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Tests & Debug", description = "Endpoints de test et debug JWT — non utilisés en production")
@RestController
@RequestMapping("/api")
public class TestController {

    /**
     * Endpoint public - Test sans authentification
     */
    @Operation(summary = "Test santé de l'API (public)")
    @GetMapping("/public/hello")
    public Map<String, Object> publicEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Public endpoint - No authentication required");
        response.put("status", "success");
        response.put("timestamp", Instant.now().toString());
        response.put("version", "Spring Boot 4.0.2");
        return response;
    }

    /**
     * Profil utilisateur - Test extraction JWT
     */
    @Operation(summary = "Profil utilisateur — extraction JWT (user, tenant-admin)")
    @GetMapping("/user/profile")
    @PreAuthorize("hasAnyRole('user', 'tenant-admin')")
    public Map<String, Object> userProfile(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();

        response.put("message", "User profile");
        response.put("sub", jwt.getSubject());
        response.put("username", jwt.getClaimAsString("preferred_username"));
        response.put("email", jwt.getClaimAsString("email"));
        response.put("email_verified", jwt.getClaimAsBoolean("email_verified"));

        // Roles
        List<String> roles = jwt.getClaimAsStringList("roles");
        response.put("roles", roles);

        // Groups (tenants)
        List<String> groups = jwt.getClaimAsStringList("groups");
        response.put("groups", groups);
        response.put("tenant_id", groups != null && !groups.isEmpty() ? groups.get(0) : null);

        // Token info
        response.put("token_issued_at", jwt.getIssuedAt());
        response.put("token_expires_at", jwt.getExpiresAt());
        response.put("token_issuer", jwt.getIssuer());

        return response;
    }

    /**
     * Dashboard admin - Test role admin
     */
    @Operation(summary = "Dashboard statistiques (admin)")
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('admin')")
    public Map<String, Object> adminDashboard(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Admin dashboard");
        response.put("admin_username", jwt.getClaimAsString("preferred_username"));
        response.put("statistics", Map.of(
                "total_users", 42,
                "total_resources", 15,
                "active_sessions", 8,
                "pending_requests", 3
        ));
        return response;
    }

    /**
     * Debug JWT - Afficher tous les claims
     */
    @Operation(summary = "Afficher tous les claims du JWT (authentifié)")
    @GetMapping("/test/jwt-claims")
    public Map<String, Object> jwtClaims(@AuthenticationPrincipal Jwt jwt) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "All JWT claims");
        response.put("claims", jwt.getClaims());
        response.put("headers", jwt.getHeaders());
        return response;
    }

    // SUPPRIMÉ : auditorLogs() - conflit avec AuditController
    // SUPPRIMÉ : pamResources() - conflit avec ResourceController

    // NOTE: Les vrais endpoints de production sont dans :
    // - ResourceController : /api/pam/resources
    // - AccessRequestController : /api/pam/requests
    // - AuditController : /api/auditor/logs
}