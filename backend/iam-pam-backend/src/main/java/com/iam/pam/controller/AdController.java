package com.iam.pam.controller;

import com.iam.pam.dto.AdConfigDTO;
import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.UserDTO;
import com.iam.pam.service.AdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Active Directory", description = "Configuration et import depuis l'AD du tenant (tenant-admin)")
@RestController
@RequestMapping("/api/admin/ad")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    @Operation(summary = "Sauvegarder la configuration AD du tenant (tenant-admin)")
    @PostMapping("/config")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<AdConfigDTO.Response>> saveConfig(
            @Valid @RequestBody AdConfigDTO.SaveRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(adService.saveConfig(request), "AD config saved")
        );
    }

    @Operation(summary = "Obtenir la configuration AD courante (tenant-admin)")
    @GetMapping("/config")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<AdConfigDTO.Response>> getConfig() {
        return ResponseEntity.ok(
                ApiResponse.success(adService.getConfig(), "AD config retrieved")
        );
    }

    @Operation(summary = "Vérifier si l'AD est configuré (tenant-admin)")
    @GetMapping("/config/status")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<Boolean>> hasConfig() {
        return ResponseEntity.ok(
                ApiResponse.success(adService.hasConfig(), "AD config status")
        );
    }

    @Operation(summary = "Tester la connexion à l'AD (tenant-admin)")
    @PostMapping("/test")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<Void>> testConnection() {
        adService.testConnection();
        return ResponseEntity.ok(ApiResponse.success(null, "AD connection successful"));
    }

    @Operation(summary = "Rechercher des utilisateurs dans l'AD du tenant (tenant-admin)")
    @GetMapping("/users/search")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<List<AdConfigDTO.AdUser>>> searchUsers(
            @RequestParam String query) {
        return ResponseEntity.ok(
                ApiResponse.success(adService.searchUsers(query), "AD users found")
        );
    }

    @Operation(summary = "Importer un utilisateur AD dans le tenant (tenant-admin)")
    @PostMapping("/users/import")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<UserDTO.TenantUser>> importUser(
            @Valid @RequestBody AdConfigDTO.ImportAdUserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(adService.importAdUser(request), "User imported from AD")
        );
    }

    @Operation(summary = "Créer un utilisateur directement dans l'Active Directory (tenant-admin)")
    @PostMapping("/users/create")
    @PreAuthorize("hasRole('tenant-admin')")
    public ResponseEntity<ApiResponse<AdConfigDTO.AdUser>> createUser(
            @Valid @RequestBody AdConfigDTO.CreateAdUserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(adService.createAdUser(request), "User created in AD")
        );
    }
}
