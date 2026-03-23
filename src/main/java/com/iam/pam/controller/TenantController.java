package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.TenantDTO;
import com.iam.pam.service.TenantManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tenants")
public class TenantController {

    private final TenantManagementService tenantManagementService;

    public TenantController(TenantManagementService tenantManagementService) {
        this.tenantManagementService = tenantManagementService;
    }

    // ========================================================================
    // SUPER-ADMIN : CRUD Tenants
    // ========================================================================

    /**
     * Créer un tenant avec ses services et sa limite d'utilisateurs
     */
    @PostMapping
    @PreAuthorize("hasRole('super-admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> createTenant(
            @Valid @RequestBody TenantDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(tenantManagementService.createTenant(request), "Tenant created")
        );
    }

    /**
     * Lister tous les tenants
     */
    @GetMapping
    @PreAuthorize("hasRole('super-admin')")
    public ResponseEntity<ApiResponse<List<TenantDTO.Response>>> getAllTenants() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getAllTenants(), "Tenants retrieved")
        );
    }

    /**
     * Obtenir un tenant par ID
     */
    @GetMapping("/{tenantId}")
    @PreAuthorize("hasRole('super-admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> getTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getTenantById(tenantId))
        );
    }

    /**
     * Mettre à jour un tenant (maxUsers, services, nom, activation)
     */
    @PutMapping("/{tenantId}")
    @PreAuthorize("hasRole('super-admin')")
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
    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasRole('super-admin')")
    public ResponseEntity<ApiResponse<Void>> deactivateTenant(@PathVariable String tenantId) {
        tenantManagementService.deactivateTenant(tenantId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tenant deactivated"));
    }

    // ========================================================================
    // TENANT ADMIN : Configuration du domaine + infos
    // ========================================================================

    /**
     * Configurer le domaine du tenant (première connexion du tenant admin)
     */
    @PostMapping("/my/domain")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> configureDomain(
            @Valid @RequestBody TenantDTO.DomainConfigRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.configureDomain(request), "Domain configured")
        );
    }

    /**
     * Vérifier si le domaine est configuré
     */
    @GetMapping("/my/domain/status")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<Boolean>> isDomainConfigured() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.isDomainConfigured())
        );
    }

    /**
     * Obtenir les infos du tenant actuel (tenant admin)
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantDTO.Response>> getMyTenant() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getMyTenant())
        );
    }
}
