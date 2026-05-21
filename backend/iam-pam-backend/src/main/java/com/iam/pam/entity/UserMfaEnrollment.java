package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_mfa_enrollment", schema = "shared",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "username"}))
public class UserMfaEnrollment {

    public enum MfaMethod { TOTP, EMAIL, SMS, WHATSAPP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "username", nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private MfaMethod method;

    /** TOTP: BASE32 secret. EMAIL/SMS: null (contact info stored separately). */
    @Column(name = "secret", length = 256)
    private String secret;

    /** EMAIL method: the verified email address. */
    @Column(name = "contact_email")
    private String contactEmail;

    /** SMS method: the verified phone number. */
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_active")
    private Boolean isActive = false;   // true only after first successful verification

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    public UserMfaEnrollment() {}

    // Getters
    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUsername() { return username; }
    public MfaMethod getMethod() { return method; }
    public String getSecret() { return secret; }
    public String getContactEmail() { return contactEmail; }
    public String getPhoneNumber() { return phoneNumber; }
    public Boolean getIsActive() { return isActive; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setUsername(String username) { this.username = username; }
    public void setMethod(MfaMethod method) { this.method = method; }
    public void setSecret(String secret) { this.secret = secret; }
    public void setContactEmail(String email) { this.contactEmail = email; }
    public void setPhoneNumber(String phone) { this.phoneNumber = phone; }
    public void setIsActive(Boolean v) { this.isActive = v; }
    public void setEnrolledAt(LocalDateTime v) { this.enrolledAt = v; }
    public void setLastVerifiedAt(LocalDateTime v) { this.lastVerifiedAt = v; }
}
