package com.iam.pam.service;

import com.iam.pam.entity.TenantService;
import com.iam.pam.entity.TenantService.ServiceType;
import com.iam.pam.repository.TenantServiceRepository;
import com.iam.pam.security.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TenantServiceChecker {

    private final TenantServiceRepository tenantServiceRepository;

    public TenantServiceChecker(TenantServiceRepository tenantServiceRepository) {
        this.tenantServiceRepository = tenantServiceRepository;
    }

    /**
     * Vérifie si le tenant actuel a accès à un service
     */
    public boolean hasAccess(ServiceType serviceType) {
        String tenantId = TenantContext.getCurrentTenant();

        return tenantServiceRepository
                .findByTenantIdAndServiceTypeAndIsActiveTrue(tenantId, serviceType)
                .map(service -> !service.isExpired())
                .orElse(false);
    }

    /**
     * Vérifie l'accès et lance une exception si refusé
     */
    public void requireAccess(ServiceType serviceType) {
        if (!hasAccess(serviceType)) {
            throw new ServiceNotSubscribedException(
                    "Access denied: Service " + serviceType + " not subscribed or expired"
            );
        }
    }

    /**
     * Liste tous les services actifs du tenant
     */
    public List<TenantService> getActiveServices() {
        String tenantId = TenantContext.getCurrentTenant();
        return tenantServiceRepository.findByTenantIdAndIsActiveTrue(tenantId);
    }

    /**
     * Ajoute un service à un tenant (Super-Admin seulement)
     */
    public TenantService addServiceToTenant(String tenantId, ServiceType serviceType, LocalDateTime expiresAt) {
        TenantService service = new TenantService(tenantId, serviceType);
        service.setExpiresAt(expiresAt);
        return tenantServiceRepository.save(service);
    }

    /**
     * Désactive un service pour un tenant
     */
    public void deactivateService(String tenantId, ServiceType serviceType) {
        tenantServiceRepository
                .findByTenantIdAndServiceTypeAndIsActiveTrue(tenantId, serviceType)
                .ifPresent(service -> {
                    service.setIsActive(false);
                    tenantServiceRepository.save(service);
                });
    }
}
