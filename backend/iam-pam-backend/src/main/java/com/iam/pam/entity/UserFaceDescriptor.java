package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_face_descriptors", schema = "shared",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "username"}))
public class UserFaceDescriptor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String username;

    @Column(name = "descriptor_json", nullable = false, columnDefinition = "TEXT")
    private String descriptorJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_active")
    private Boolean isActive = true;

    public UserFaceDescriptor() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDescriptorJson() { return descriptorJson; }
    public void setDescriptorJson(String descriptorJson) { this.descriptorJson = descriptorJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
