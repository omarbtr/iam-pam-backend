package com.iam.pam.service;

import com.iam.pam.dto.TenantDTO;
import com.iam.pam.dto.TenantServiceDTO;
import com.iam.pam.entity.Tenant;
import com.iam.pam.entity.TenantService;
import com.iam.pam.entity.TenantService.ServiceType;
import com.iam.pam.repository.ResourceRepository;
import com.iam.pam.repository.TenantRepository;
import com.iam.pam.repository.TenantServiceRepository;
import com.iam.pam.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TenantManagementService {

    private static final Logger log = LoggerFactory.getLogger(TenantManagementService.class);

    private final TenantRepository tenantRepository;
    private final TenantServiceRepository tenantServiceRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final ResourceRepository resourceRepository;
    private final AuditRetentionService auditRetentionService;

    public TenantManagementService(TenantRepository tenantRepository,
                                    TenantServiceRepository tenantServiceRepository,
                                    KeycloakAdminService keycloakAdminService,
                                    ResourceRepository resourceRepository,
                                    AuditRetentionService auditRetentionService) {
        this.tenantRepository = tenantRepository;
        this.tenantServiceRepository = tenantServiceRepository;
        this.keycloakAdminService = keycloakAdminService;
        this.resourceRepository = resourceRepository;
        this.auditRetentionService = auditRetentionService;
    }

    // ========================================================================
    // CRUD TENANT (Super-Admin)
    // ========================================================================

    /**
     * Créer un nouveau tenant avec ses services et sa limite d'utilisateurs.
     * Crée aussi le groupe Keycloak correspondant.
     */
    public TenantDTO.Response createTenant(TenantDTO.CreateRequest dto) {
        if (tenantRepository.existsByTenantId(dto.getTenantId())) {
            throw new IllegalArgumentException("Tenant with ID '" + dto.getTenantId() + "' already exists");
        }

        // Créer le tenant
        String schemaName = dto.getTenantId().replace("-", "_");
        Tenant tenant = new Tenant(dto.getTenantId(), dto.getTenantName(), schemaName, dto.getMaxUsers());
        if (dto.getMaxResources() != null) {
            tenant.setMaxResources(dto.getMaxResources());
        }
        tenant = tenantRepository.save(tenant);

        // Assigner les services demandés
        for (ServiceType serviceType : dto.getServices()) {
            TenantService service = new TenantService(dto.getTenantId(), serviceType);
            tenantServiceRepository.save(service);
        }

        // Créer le groupe Keycloak pour ce tenant (non bloquant)
        try {
            keycloakAdminService.createTenantGroup(dto.getTenantId());
        } catch (Exception e) {
            log.warn("Keycloak group creation failed for tenant {} (tenant saved in DB): {}",
                    dto.getTenantId(), e.getMessage());
        }

        log.info("Tenant created: {} with services: {} and maxUsers: {}",
                dto.getTenantId(), dto.getServices(), dto.getMaxUsers());

        return toResponse(tenant);
    }

    /**
     * Mettre à jour un tenant (maxUsers, services, activation)
     */
    public TenantDTO.Response updateTenant(String tenantId, TenantDTO.UpdateRequest dto) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        if (dto.getTenantName() != null) {
            tenant.setTenantName(dto.getTenantName());
        }

        if (dto.getMaxUsers() != null) {
            if (dto.getMaxUsers() < tenant.getCurrentUserCount()) {
                throw new IllegalArgumentException(
                        "Cannot set maxUsers to " + dto.getMaxUsers() +
                                " because tenant already has " + tenant.getCurrentUserCount() + " users");
            }
            tenant.setMaxUsers(dto.getMaxUsers());
        }

        if (dto.getMaxResources() != null) {
            long current = resourceRepository.countByTenantIdAndIsActiveTrue(tenantId);
            if (dto.getMaxResources() < current) {
                throw new IllegalArgumentException(
                        "Cannot set maxResources to " + dto.getMaxResources() +
                                " because tenant already has " + current + " active resources");
            }
            tenant.setMaxResources(dto.getMaxResources());
        }

        if (dto.getIsActive() != null) {
            tenant.setIsActive(dto.getIsActive());
        }

        tenant = tenantRepository.save(tenant);

        // Mettre à jour les services si fournis
        if (dto.getServices() != null) {
            updateTenantServices(tenantId, dto.getServices());
        }

        log.info("Tenant updated: {}", tenantId);
        return toResponse(tenant);
    }

    /**
     * Lister tous les tenants (super-admin)
     */
    @Transactional(readOnly = true)
    public List<TenantDTO.Response> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtenir un tenant par son ID
     */
    @Transactional(readOnly = true)
    public TenantDTO.Response getTenantById(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        return toResponse(tenant);
    }

    /**
     * Désactiver un tenant
     */
    public void deactivateTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        tenant.setIsActive(false);
        tenantRepository.save(tenant);
        log.info("Tenant deactivated: {}", tenantId);
    }

    // ========================================================================
    // CONFIGURATION DOMAINE (Tenant Admin - première connexion)
    // ========================================================================

    /**
     * Configurer le domaine du tenant (première connexion du tenant admin)
     */
    public TenantDTO.Response configureDomain(TenantDTO.DomainConfigRequest dto) {
        String tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        if (tenant.getDomainConfigured()) {
            throw new IllegalStateException("Domain already configured for this tenant");
        }

        if (tenantRepository.existsByDomain(dto.getDomain())) {
            throw new IllegalArgumentException("Domain '" + dto.getDomain() + "' is already taken");
        }

        tenant.setDomain(dto.getDomain());
        tenant.setDomainConfigured(true);
        tenant = tenantRepository.save(tenant);

        log.info("Domain configured for tenant {}: {}", tenantId, dto.getDomain());
        return toResponse(tenant);
    }

    /**
     * Vérifier si le tenant actuel a configuré son domaine
     */
    @Transactional(readOnly = true)
    public boolean isDomainConfigured() {
        String tenantId = TenantContext.getCurrentTenant();
        return tenantRepository.findByTenantId(tenantId)
                .map(Tenant::getDomainConfigured)
                .orElse(false);
    }

    /**
     * Obtenir les infos du tenant actuel (pour le tenant admin)
     */
    @Transactional(readOnly = true)
    public TenantDTO.Response getMyTenant() {
        String tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        return toResponse(tenant);
    }

    // ========================================================================
    // GESTION DES SERVICES DU TENANT
    // ========================================================================

    /**
     * Obtenir les services actifs du tenant actuel
     */
    @Transactional(readOnly = true)
    public List<TenantServiceDTO.Response> getMyServices() {
        String tenantId = TenantContext.getCurrentTenant();
        return tenantServiceRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(this::toServiceResponse)
                .collect(Collectors.toList());
    }

    /**
     * Définir la limite de ressources d'un tenant (super-admin)
     */
    public TenantDTO.Response setResourceLimit(String tenantId, Integer maxResources) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        if (maxResources != null) {
            long current = resourceRepository.countByTenantIdAndIsActiveTrue(tenantId);
            if (maxResources < current) {
                throw new IllegalArgumentException(
                        "Cannot set maxResources to " + maxResources +
                                " because tenant already has " + current + " active resources");
            }
        }
        tenant.setMaxResources(maxResources);
        tenant = tenantRepository.save(tenant);
        log.info("Resource limit for tenant {} set to {}", tenantId, maxResources);
        return toResponse(tenant);
    }

    /**
     * Mettre à jour la période de rétention des logs du tenant actuel (tenant-admin).
     * Applique immédiatement la purge si la nouvelle période est plus courte.
     */
    public TenantDTO.Response updateRetention(int days) {
        if (days != 7 && days != 14 && days != 30) {
            throw new IllegalArgumentException("Retention must be 7, 14 or 30 days");
        }
        String tenantId = TenantContext.getCurrentTenant();
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        int previous = tenant.getAuditLogRetentionDays();
        tenant.setAuditLogRetentionDays(days);
        tenant = tenantRepository.save(tenant);

        // If the new period is shorter, purge excess logs immediately
        if (days < previous) {
            auditRetentionService.applyRetention(tenantId, days);
        }
        log.info("Tenant {} retention updated: {} → {} days", tenantId, previous, days);
        return toResponse(tenant);
    }

    /**
     * Vérifier si le tenant peut encore ajouter des utilisateurs
     */
    @Transactional(readOnly = true)
    public boolean canAddUser(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        return tenant.canAddUser();
    }

    // ========================================================================
    // MÉTHODES PRIVÉES
    // ========================================================================

    private void updateTenantServices(String tenantId, List<ServiceType> newServices) {
        // Désactiver tous les services existants
        List<TenantService> existingServices = tenantServiceRepository.findByTenantIdAndIsActiveTrue(tenantId);
        for (TenantService existing : existingServices) {
            if (!newServices.contains(existing.getServiceType())) {
                existing.setIsActive(false);
                tenantServiceRepository.save(existing);
            }
        }

        // Ajouter les nouveaux services
        List<ServiceType> existingTypes = existingServices.stream()
                .map(TenantService::getServiceType)
                .collect(Collectors.toList());

        for (ServiceType serviceType : newServices) {
            if (!existingTypes.contains(serviceType)) {
                TenantService service = new TenantService(tenantId, serviceType);
                tenantServiceRepository.save(service);
            }
        }
    }

    private TenantDTO.Response toResponse(Tenant tenant) {
        TenantDTO.Response response = new TenantDTO.Response();
        response.setId(tenant.getId());
        response.setTenantId(tenant.getTenantId());
        response.setTenantName(tenant.getTenantName());
        response.setDomain(tenant.getDomain());
        response.setDomainConfigured(tenant.getDomainConfigured());
        response.setMaxUsers(tenant.getMaxUsers());
        response.setCurrentUserCount(tenant.getCurrentUserCount());
        response.setMaxResources(tenant.getMaxResources());
        response.setCurrentResources((int) resourceRepository.countByTenantIdAndIsActiveTrue(tenant.getTenantId()));
        response.setAuditLogRetentionDays(tenant.getAuditLogRetentionDays());
        response.setIsActive(tenant.getIsActive());
        response.setAdminUsername(tenant.getAdminUsername());
        response.setCreatedAt(tenant.getCreatedAt());
        response.setUpdatedAt(tenant.getUpdatedAt());

        // Charger les services actifs
        List<String> services = tenantServiceRepository
                .findByTenantIdAndIsActiveTrue(tenant.getTenantId())
                .stream()
                .map(s -> s.getServiceType().name())
                .collect(Collectors.toList());
        response.setServices(services);

        return response;
    }

    private TenantServiceDTO.Response toServiceResponse(TenantService service) {
        return new TenantServiceDTO.Response(
                service.getId(),
                service.getServiceType(),
                service.getIsActive(),
                service.getSubscribedAt(),
                service.getExpiresAt(),
                service.isExpired()
        );
    }
}
