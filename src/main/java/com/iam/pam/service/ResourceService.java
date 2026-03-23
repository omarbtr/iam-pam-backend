package com.iam.pam.service;

import com.iam.pam.dto.ResourceDTO;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.Resource;
import com.iam.pam.repository.ResourceRepository;
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
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceRepository resourceRepository;
    private final AuditService auditService;

    public ResourceService(ResourceRepository resourceRepository, AuditService auditService) {
        this.resourceRepository = resourceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ResourceDTO.Response> getAllResources() {
        String tenantId = TenantContext.getCurrentTenant();
        log.debug("Getting all resources for tenant: {}", tenantId);

        return resourceRepository.findByTenantIdAndIsActiveTrue(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ResourceDTO.Response getById(Long id) {
        String tenantId = TenantContext.getCurrentTenant();
        Resource resource = findByIdAndTenant(id, tenantId);
        return toResponse(resource);
    }

    public ResourceDTO.Response create(ResourceDTO.Request dto, String username) {
        String tenantId = TenantContext.getCurrentTenant();

        if (resourceRepository.existsByNameAndTenantId(dto.getName(), tenantId)) {
            throw new IllegalArgumentException(
                    "Resource with name '" + dto.getName() + "' already exists");
        }

        Resource resource = new Resource();
        resource.setName(dto.getName());
        resource.setType(dto.getType());
        resource.setHost(dto.getHost());
        resource.setPort(dto.getPort());
        resource.setDescription(dto.getDescription());
        resource.setTenantId(tenantId);
        resource.setIsActive(true);

        Resource saved = resourceRepository.save(resource);
        log.info("Resource created: {} by {} in tenant {}", saved.getName(), username, tenantId);

        auditService.log(
                AuditLog.AuditAction.RESOURCE_CREATED,
                username,
                tenantId,
                saved.getName(),
                null,
                "Resource created: " + saved.getName()
        );

        return toResponse(saved);
    }

    public ResourceDTO.Response update(Long id, ResourceDTO.Request dto, String username) {
        String tenantId = TenantContext.getCurrentTenant();
        Resource resource = findByIdAndTenant(id, tenantId);

        if (!resource.getName().equals(dto.getName()) &&
                resourceRepository.existsByNameAndTenantId(dto.getName(), tenantId)) {
            throw new IllegalArgumentException(
                    "Resource with name '" + dto.getName() + "' already exists");
        }

        resource.setName(dto.getName());
        resource.setType(dto.getType());
        resource.setHost(dto.getHost());
        resource.setPort(dto.getPort());
        resource.setDescription(dto.getDescription());

        Resource saved = resourceRepository.save(resource);
        log.info("Resource updated: {} by {}", saved.getName(), username);

        auditService.log(
                AuditLog.AuditAction.RESOURCE_UPDATED,
                username,
                tenantId,
                saved.getName(),
                null,
                "Resource updated: " + saved.getName()
        );

        return toResponse(saved);
    }

    public void delete(Long id, String username) {
        String tenantId = TenantContext.getCurrentTenant();
        Resource resource = findByIdAndTenant(id, tenantId);

        resource.setIsActive(false);
        resourceRepository.save(resource);
        log.info("Resource deactivated: {} by {}", resource.getName(), username);

        auditService.log(
                AuditLog.AuditAction.RESOURCE_DELETED,
                username,
                tenantId,
                resource.getName(),
                null,
                "Resource deactivated: " + resource.getName()
        );
    }

    @Transactional(readOnly = true)
    public List<ResourceDTO.Response> getByType(Resource.ResourceType type) {
        String tenantId = TenantContext.getCurrentTenant();
        return resourceRepository.findByTypeAndTenantId(type, tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean exists(String name) {
        String tenantId = TenantContext.getCurrentTenant();
        return resourceRepository.existsByNameAndTenantId(name, tenantId);
    }

    private Resource findByIdAndTenant(Long id, String tenantId) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Resource not found with id: " + id));

        if (!resource.getTenantId().equals(tenantId)) {
            log.warn("Tenant {} tried to access resource {} from tenant {}",
                    tenantId, id, resource.getTenantId());
            throw new SecurityException("Access denied: resource belongs to another tenant");
        }

        return resource;
    }

    private ResourceDTO.Response toResponse(Resource resource) {
        return new ResourceDTO.Response(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getHost(),
                resource.getPort(),
                resource.getDescription(),
                resource.getTenantId(),
                resource.getIsActive(),
                resource.getCreatedAt()
        );
    }
}