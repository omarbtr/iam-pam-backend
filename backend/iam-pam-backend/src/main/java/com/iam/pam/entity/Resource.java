package com.iam.pam.entity;

import com.iam.pam.config.AesEncryptionConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Entity
@Table(name = "resources")
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ResourceType type;

    @NotBlank
    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "description")
    private String description;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "credential_username")
    private String credentialUsername;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "credential_password")
    private String credentialPassword;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "credential_private_key", columnDefinition = "TEXT")
    private String credentialPrivateKey;

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
    public Resource() {}

    public Resource(Long id, String name, ResourceType type, String host, Integer port,
                    String description, String tenantId, Boolean isActive) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.description = description;
        this.tenantId = tenantId;
        this.isActive = isActive;
    }

    // GETTERS
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ResourceType getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDescription() {
        return description;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // SETTERS
    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(ResourceType type) {
        this.type = type;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getCredentialUsername() { return credentialUsername; }
    public void setCredentialUsername(String credentialUsername) { this.credentialUsername = credentialUsername; }

    public String getCredentialPassword() { return credentialPassword; }
    public void setCredentialPassword(String credentialPassword) { this.credentialPassword = credentialPassword; }

    public String getCredentialPrivateKey() { return credentialPrivateKey; }
    public void setCredentialPrivateKey(String credentialPrivateKey) { this.credentialPrivateKey = credentialPrivateKey; }

    public boolean hasCredentials() {
        return credentialUsername != null && !credentialUsername.isBlank()
                && (credentialPassword != null || credentialPrivateKey != null);
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public enum ResourceType {
        SSH,
        RDP,
        DATABASE,
        WEB,
        API
    }
}