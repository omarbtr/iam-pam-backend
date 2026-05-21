package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.TenantDTO;
import com.iam.pam.dto.UserDTO;
import com.iam.pam.service.KeycloakAdminService;
import com.iam.pam.service.TenantManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Tenant Management", description = "Gestion des tenants (admin) et configuration domaine (tenant-admin)")
@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {

    private final TenantManagementService tenantManagementService;
    private final KeycloakAdminService keycloakAdminService;

    public TenantController(TenantManagementService tenantManagementService,
                            KeycloakAdminService keycloakAdminService) {
        this.tenantManagementService = tenantManagementService;
        this.keycloakAdminService = keycloakAdminService;
    }

    // ========================================================================
    // SUPER-ADMIN : CRUD Tenants
    // ========================================================================

    /**
     * Créer un tenant avec ses services et sa limite d'utilisateurs
     */
    @Operation(summary = "Créer un tenant (admin)")
    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> createTenant(
            @Valid @RequestBody TenantDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(tenantManagementService.createTenant(request), "Tenant created")
        );
    }

    /**
     * Lister tous les tenants
     */
    @Operation(summary = "Lister tous les tenants (admin)")
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<List<TenantDTO.Response>>> getAllTenants() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getAllTenants(), "Tenants retrieved")
        );
    }

    /**
     * Obtenir un tenant par ID
     */
    @Operation(summary = "Obtenir un tenant par ID (admin)")
    @GetMapping("/{tenantId}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> getTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getTenantById(tenantId))
        );
    }

    /**
     * Mettre à jour un tenant (maxUsers, services, nom, activation)
     */
    @Operation(summary = "Mettre à jour un tenant - maxUsers, services, nom (admin)")
    @PutMapping("/{tenantId}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> updateTenant(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantDTO.UpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.updateTenant(tenantId, request), "Tenant updated")
        );
    }

    /**
     * Désactiver un tenant
     */
    @Operation(summary = "Désactiver un tenant (admin)")
    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<Void>> deactivateTenant(@PathVariable String tenantId) {
        tenantManagementService.deactivateTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tenant deactivated"));
    }

    /**
     * Réactiver un tenant
     */
    @Operation(summary = "Réactiver un tenant (admin)")
    @PutMapping("/{tenantId}/activate")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> activateTenant(@PathVariable String tenantId) {
        TenantDTO.UpdateRequest req = new TenantDTO.UpdateRequest();
        req.setIsActive(true);
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.updateTenant(tenantId, req), "Tenant activated")
        );
    }

    /**
     * Définir la limite de ressources d'un tenant
     */
    @Operation(summary = "Définir la limite de ressources d'un tenant (admin)")
    @PatchMapping("/{tenantId}/resource-limit")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> setResourceLimit(
            @PathVariable String tenantId,
            @RequestParam(required = false) Integer maxResources) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.setResourceLimit(tenantId, maxResources),
                        "Resource limit updated")
        );
    }

    /**
     * Assigner un utilisateur comme tenant-admin d'un tenant
     * (ajoute l'utilisateur au groupe Keycloak + lui donne le rôle tenant-admin)
     */
    @Operation(summary = "Assigner un tenant-admin à un tenant (admin)")
    @PostMapping("/{tenantId}/admins")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<UserDTO.TenantUser>> assignAdmin(
            @PathVariable String tenantId,
            @Valid @RequestBody UserDTO.AdminAssignRequest request) {
        UserDTO.TenantUser user = keycloakAdminService.assignTenantAdmin(tenantId, request.getUsername());
        return ResponseEntity.ok(ApiResponse.success(user, "Admin assigned to tenant"));
    }

    // ========================================================================
    // TENANT ADMIN : Configuration du domaine + infos
    // ========================================================================

    /**
     * Mettre à jour la période de rétention des logs (tenant-admin)
     */
    @Operation(summary = "Configurer la rétention des journaux d'audit (tenant-admin)")
    @PatchMapping("/my/retention")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> updateRetention(
            @Valid @RequestBody TenantDTO.RetentionUpdateRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        tenantManagementService.updateRetention(request.getRetentionDays()),
                        "Retention updated")
        );
    }

    /**
     * Configurer le domaine du tenant (première connexion du tenant admin)
     */
    @Operation(summary = "Configurer le domaine - 1ère connexion obligatoire (tenant-admin)")
    @PostMapping("/my/domain")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> configureDomain(
            @Valid @RequestBody TenantDTO.DomainConfigRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.configureDomain(request), "Domain configured")
        );
    }

    /**
     * Vérifier si le domaine est configuré
     */
    @Operation(summary = "Vérifier si le domaine est configuré (tenant-admin)")
    @GetMapping("/my/domain/status")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<Boolean>> isDomainConfigured() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.isDomainConfigured())
        );
    }

    /**
     * Obtenir les infos du tenant actuel (tenant admin)
     */
    @Operation(summary = "Obtenir les infos de mon tenant (tenant-admin)")
    @GetMapping("/my")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> getMyTenant() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getMyTenant())
        );
    }
}
