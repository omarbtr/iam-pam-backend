package com.iam.pam.dto;

import com.iam.pam.entity.TenantService.ServiceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class TenantDTO {

    /**
     * Requête de création d'un tenant par le super-admin.
     * Inclut les services souscrits et la limite d'utilisateurs.
     */
    public static class CreateRequest {
        @NotBlank(message = "Tenant ID is required")
        private String tenantId;

        @NotBlank(message = "Tenant name is required")
        private String tenantName;

        @NotNull(message = "Max users is required")
        @Min(value = 1, message = "Max users must be at least 1")
        private Integer maxUsers;

        @NotEmpty(message = "At least one service must be assigned")
        private List<ServiceType> services;

        // Constructeurs
        public CreateRequest() {}

        public CreateRequest(String tenantId, String tenantName, Integer maxUsers, List<ServiceType> services) {
            this.tenantId = tenantId;
            this.tenantName = tenantName;
            this.maxUsers = maxUsers;
            this.services = services;
        }

        // Getters/Setters
        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }

        public Integer getMaxUsers() { return maxUsers; }
        public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }

        public List<ServiceType> getServices() { return services; }
        public void setServices(List<ServiceType> services) { this.services = services; }
    }

    /**
     * Requête de mise à jour d'un tenant (super-admin peut modifier maxUsers, services, etc.)
     */
    public static class UpdateRequest {
        private String tenantName;

        @Min(value = 1, message = "Max users must be at least 1")
        private Integer maxUsers;

        private List<ServiceType> services;

        private Boolean isActive;

        public UpdateRequest() {}

        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }

        public Integer getMaxUsers() { return maxUsers; }
        public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }

        public List<ServiceType> getServices() { return services; }
        public void setServices(List<ServiceType> services) { this.services = services; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }

    /**
     * Requête de configuration du domaine par le tenant admin (première connexion)
     */
    public static class DomainConfigRequest {
        @NotBlank(message = "Domain is required")
        private String domain;

        public DomainConfigRequest() {}

        public DomainConfigRequest(String domain) {
            this.domain = domain;
        }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
    }

    /**
     * Réponse tenant complète
     */
    public static class Response {
        private Long id;
        private String tenantId;
        private String tenantName;
        private String domain;
        private Boolean domainConfigured;
        private Integer maxUsers;
        private Integer currentUserCount;
        private Boolean isActive;
        private List<String> services;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Response() {}

        // Getters/Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public Boolean getDomainConfigured() { return domainConfigured; }
        public void setDomainConfigured(Boolean domainConfigured) { this.domainConfigured = domainConfigured; }

        public Integer getMaxUsers() { return maxUsers; }
        public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }

        public Integer getCurrentUserCount() { return currentUserCount; }
        public void setCurrentUserCount(Integer currentUserCount) { this.currentUserCount = currentUserCount; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

        public List<String> getServices() { return services; }
        public void setServices(List<String> services) { this.services = services; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
