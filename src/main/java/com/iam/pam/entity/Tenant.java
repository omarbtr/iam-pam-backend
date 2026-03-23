package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenants", schema = "shared")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", unique = true, nullable = false)
    private String tenantId;

    @Column(name = "tenant_name", nullable = false)
    private String tenantName;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    // Domaine du tenant (ex: company.iam-pam.com) - configuré par le tenant admin à la 1ère connexion
    @Column(name = "domain")
    private String domain;

    @Column(name = "domain_configured")
    private Boolean domainConfigured = false;

    // Nombre maximum d'utilisateurs autorisés pour ce tenant
    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 5;

    // Nombre actuel d'utilisateurs dans ce tenant
    @Column(name = "current_user_count")
    private Integer currentUserCount = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // CONSTRUCTEURS
    public Tenant() {}

    public Tenant(String tenantId, String tenantName, String schemaName, Integer maxUsers) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.schemaName = schemaName;
        this.maxUsers = maxUsers;
    }

    // Méthode utilitaire
    public boolean canAddUser() {
        return currentUserCount < maxUsers;
    }

    public void incrementUserCount() {
        this.currentUserCount++;
    }

    public void decrementUserCount() {
        if (this.currentUserCount > 0) {
            this.currentUserCount--;
        }
    }

    // GETTERS
    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTenantName() { return tenantName; }
    public String getSchemaName() { return schemaName; }
    public String getDomain() { return domain; }
    public Boolean getDomainConfigured() { return domainConfigured; }
    public Integer getMaxUsers() { return maxUsers; }
    public Integer getCurrentUserCount() { return currentUserCount; }
    public Boolean getIsActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // SETTERS
    public void setId(Long id) { this.id = id; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setDomainConfigured(Boolean domainConfigured) { this.domainConfigured = domainConfigured; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public void setCurrentUserCount(Integer currentUserCount) { this.currentUserCount = currentUserCount; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
