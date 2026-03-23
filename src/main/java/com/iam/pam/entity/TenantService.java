package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_services", schema = "shared")
public class TenantService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false)
    private ServiceType serviceType;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "subscribed_at")
    private LocalDateTime subscribedAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // NULL = illimité

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Constructeurs
    public TenantService() {}

    public TenantService(String tenantId, ServiceType serviceType) {
        this.tenantId = tenantId;
        this.serviceType = serviceType;
        this.isActive = true;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getSubscribedAt() { return subscribedAt; }
    public void setSubscribedAt(LocalDateTime subscribedAt) { this.subscribedAt = subscribedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Méthode utilitaire
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    // Enum des services
    public enum ServiceType {
        IAM,        // Identity & Access Management
        PAM,        // Privileged Access Management
        MFA,        // Multi-Factor Authentication
        SSO,        // Single Sign-On
        AUDIT,      // Audit & Compliance
        REPORTING   // Reporting & Analytics
    }
}