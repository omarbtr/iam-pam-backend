package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.UserDTO;
import com.iam.pam.security.TenantContext;
import com.iam.pam.service.KeycloakAdminService;
import com.iam.pam.service.TenantManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final KeycloakAdminService keycloakAdminService;
    private final TenantManagementService tenantManagementService;

    public UserController(KeycloakAdminService keycloakAdminService,
                          TenantManagementService tenantManagementService) {
        this.keycloakAdminService = keycloakAdminService;
        this.tenantManagementService = tenantManagementService;
    }

    // ========================================================================
    // ANNUAIRE : Recherche dans AD/LDAP (via Keycloak)
    // ========================================================================

    /**
     * Rechercher des utilisateurs dans l'annuaire AD/LDAP
     */
    @GetMapping("/directory/search")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<List<UserDTO.DirectoryUser>>> searchDirectory(
            @RequestParam String query) {
        return ResponseEntity.ok(
                ApiResponse.success(keycloakAdminService.searchDirectoryUsers(query),
                        "Directory users found")
        );
    }

    /**
     * Lister les utilisateurs de l'annuaire (paginé)
     */
    @GetMapping("/directory")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<List<UserDTO.DirectoryUser>>> listDirectory(
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "20") int max) {
        return ResponseEntity.ok(
                ApiResponse.success(keycloakAdminService.listDirectoryUsers(first, max),
                        "Directory users listed")
        );
    }

    // ========================================================================
    // IMPORT : Importer un utilisateur de l'annuaire dans le tenant
    // ========================================================================

    /**
     * Importer un utilisateur de l'annuaire dans le tenant actuel
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<UserDTO.TenantUser>> importUser(
            @Valid @RequestBody UserDTO.ImportRequest request) {

        String tenantId = TenantContext.getCurrentTenant();
        UserDTO.TenantUser user = keycloakAdminService.importUserToTenant(
                tenantId, request.getUsername(), request.getRoles());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(user, "User imported from directory")
        );
    }

    /**
     * Retirer un utilisateur du tenant
     */
    @DeleteMapping("/{username}")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<Void>> removeUser(@PathVariable String username) {
        String tenantId = TenantContext.getCurrentTenant();
        keycloakAdminService.removeUserFromTenant(tenantId, username);
        return ResponseEntity.ok(ApiResponse.success(null, "User removed from tenant"));
    }

    // ========================================================================
    // UTILISATEURS DU TENANT
    // ========================================================================

    /**
     * Lister les utilisateurs du tenant actuel
     */
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<List<UserDTO.TenantUser>>> getTenantUsers() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                ApiResponse.success(keycloakAdminService.getTenantUsers(tenantId),
                        "Tenant users retrieved")
        );
    }

    /**
     * Vérifier si le tenant peut encore ajouter des utilisateurs
     */
    @GetMapping("/can-add")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<Boolean>> canAddUser() {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.canAddUser(tenantId))
        );
    }
}
