package com.iam.pam.controller;

import com.iam.pam.dto.ApiResponse;
import com.iam.pam.dto.TenantServiceDTO;
import com.iam.pam.service.TenantManagementService;
import com.iam.pam.service.TenantServiceChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Service Subscriptions", description = "Consultation des services souscrits (tenant-admin, user) et abonnement (admin)")
@RestController
@RequestMapping("/api/tenant/services")
public class TenantServiceController {

    private final TenantManagementService tenantManagementService;
    private final TenantServiceChecker tenantServiceChecker;

    public TenantServiceController(TenantManagementService tenantManagementService,
                                    TenantServiceChecker tenantServiceChecker) {
        this.tenantManagementService = tenantManagementService;
        this.tenantServiceChecker = tenantServiceChecker;
    }

    /**
     * Liste les services actifs du tenant actuel
     */
    @Operation(summary = "Lister les services actifs de mon tenant (tenant-admin, user)")
    @GetMapping
    @PreAuthorize("hasAnyRole('tenant-admin', 'user')")
    public ResponseEntity<ApiResponse<List<TenantServiceDTO.Response>>> getMyServices() {
        return ResponseEntity.ok(
                ApiResponse.success(tenantManagementService.getMyServices(), "Services retrieved")
        );
    }

    /**
     * Activer un service pour le tenant (super-admin seulement via TenantController)
     */
    @Operation(summary = "Abonner un service à un tenant (admin)")
    @PostMapping("/subscribe")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<ApiResponse<TenantServiceDTO.Response>> subscribe(
            @Valid @RequestBody TenantServiceDTO.SubscribeRequest request) {

        var service = tenantServiceChecker.addServiceToTenant(
                com.iam.pam.security.TenantContext.getCurrentTenant(),
                request.getServiceType(),
                request.getExpiresAt());

        TenantServiceDTO.Response response = new TenantServiceDTO.Response(
                service.getId(),
                service.getServiceType(),
                service.getIsActive(),
                service.getSubscribedAt(),
                service.getExpiresAt(),
                service.isExpired()
        );

        return ResponseEntity.ok(ApiResponse.success(response, "Service subscribed"));
    }
}
