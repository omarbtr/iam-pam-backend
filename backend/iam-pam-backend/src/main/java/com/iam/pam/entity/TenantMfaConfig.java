package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_mfa_config", schema = "shared")
public class TenantMfaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", unique = true, nullable = false)
    private String tenantId;

    @Column(name = "totp_enabled")
    private Boolean totpEnabled = false;

    @Column(name = "email_otp_enabled")
    private Boolean emailOtpEnabled = false;

    @Column(name = "sms_otp_enabled")
    private Boolean smsOtpEnabled = false;

    @Column(name = "whatsapp_otp_enabled")
    private Boolean whatsappOtpEnabled = false;

    /** When true, every user in this tenant must complete MFA before accessing the app. */
    @Column(name = "mfa_required")
    private Boolean mfaRequired = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { this.updatedAt = LocalDateTime.now(); }

    public TenantMfaConfig() {}

    public TenantMfaConfig(String tenantId) { this.tenantId = tenantId; }

    // Getters
    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public Boolean getTotpEnabled() { return totpEnabled; }
    public Boolean getEmailOtpEnabled() { return emailOtpEnabled; }
    public Boolean getSmsOtpEnabled() { return smsOtpEnabled; }
    public Boolean getWhatsappOtpEnabled() { return whatsappOtpEnabled; }
    public Boolean getMfaRequired() { return mfaRequired; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setTotpEnabled(Boolean v) { this.totpEnabled = v; }
    public void setEmailOtpEnabled(Boolean v) { this.emailOtpEnabled = v; }
    public void setSmsOtpEnabled(Boolean v) { this.smsOtpEnabled = v; }
    public void setWhatsappOtpEnabled(Boolean v) { this.whatsappOtpEnabled = v; }
    public void setMfaRequired(Boolean v) { this.mfaRequired = v; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }

    /** Returns true if at least one method is enabled. */
    public boolean hasAnyMethodEnabled() {
        return Boolean.TRUE.equals(totpEnabled)
                || Boolean.TRUE.equals(emailOtpEnabled)
                || Boolean.TRUE.equals(smsOtpEnabled)
                || Boolean.TRUE.equals(whatsappOtpEnabled);
    }
}
