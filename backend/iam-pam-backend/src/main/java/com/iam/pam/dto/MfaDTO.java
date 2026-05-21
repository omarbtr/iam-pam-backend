package com.iam.pam.dto;

import com.iam.pam.entity.UserMfaEnrollment.MfaMethod;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class MfaDTO {

    // ── Tenant-admin: configure enabled methods ──────────────────────────────

    public static class TenantConfigRequest {
        private Boolean totpEnabled;
        private Boolean emailOtpEnabled;
        private Boolean smsOtpEnabled;
        private Boolean whatsappOtpEnabled;
        private Boolean mfaRequired;

        public Boolean getTotpEnabled() { return totpEnabled; }
        public void setTotpEnabled(Boolean v) { this.totpEnabled = v; }
        public Boolean getEmailOtpEnabled() { return emailOtpEnabled; }
        public void setEmailOtpEnabled(Boolean v) { this.emailOtpEnabled = v; }
        public Boolean getSmsOtpEnabled() { return smsOtpEnabled; }
        public void setSmsOtpEnabled(Boolean v) { this.smsOtpEnabled = v; }
        public Boolean getWhatsappOtpEnabled() { return whatsappOtpEnabled; }
        public void setWhatsappOtpEnabled(Boolean v) { this.whatsappOtpEnabled = v; }
        public Boolean getMfaRequired() { return mfaRequired; }
        public void setMfaRequired(Boolean v) { this.mfaRequired = v; }
    }

    public static class TenantConfigResponse {
        private String tenantId;
        private boolean totpEnabled;
        private boolean emailOtpEnabled;
        private boolean smsOtpEnabled;
        private boolean whatsappOtpEnabled;
        private boolean mfaRequired;

        public String getTenantId() { return tenantId; }
        public void setTenantId(String v) { this.tenantId = v; }
        public boolean isTotpEnabled() { return totpEnabled; }
        public void setTotpEnabled(boolean v) { this.totpEnabled = v; }
        public boolean isEmailOtpEnabled() { return emailOtpEnabled; }
        public void setEmailOtpEnabled(boolean v) { this.emailOtpEnabled = v; }
        public boolean isSmsOtpEnabled() { return smsOtpEnabled; }
        public void setSmsOtpEnabled(boolean v) { this.smsOtpEnabled = v; }
        public boolean isWhatsappOtpEnabled() { return whatsappOtpEnabled; }
        public void setWhatsappOtpEnabled(boolean v) { this.whatsappOtpEnabled = v; }
        public boolean isMfaRequired() { return mfaRequired; }
        public void setMfaRequired(boolean v) { this.mfaRequired = v; }
    }

    // ── User: enrollment ─────────────────────────────────────────────────────

    /** Step 1: user picks a method → backend returns setup info (TOTP secret/QR, etc.) */
    public static class InitEnrollRequest {
        @NotNull
        private MfaMethod method;
        private String contactEmail;   // required for EMAIL method
        private String phoneNumber;    // required for SMS method

        public MfaMethod getMethod() { return method; }
        public void setMethod(MfaMethod v) { this.method = v; }
        public String getContactEmail() { return contactEmail; }
        public void setContactEmail(String v) { this.contactEmail = v; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String v) { this.phoneNumber = v; }
    }

    public static class InitEnrollResponse {
        private MfaMethod method;
        private String totpSecret;      // raw base32 secret (for manual entry)
        private String totpQrUri;       // otpauth:// URI (scan with authenticator app)
        private String message;         // human-readable instruction

        public MfaMethod getMethod() { return method; }
        public void setMethod(MfaMethod v) { this.method = v; }
        public String getTotpSecret() { return totpSecret; }
        public void setTotpSecret(String v) { this.totpSecret = v; }
        public String getTotpQrUri() { return totpQrUri; }
        public void setTotpQrUri(String v) { this.totpQrUri = v; }
        public String getMessage() { return message; }
        public void setMessage(String v) { this.message = v; }
    }

    /** Step 2: user submits OTP/TOTP code to confirm enrollment. */
    public static class ConfirmEnrollRequest {
        @NotNull
        private String code;

        public String getCode() { return code; }
        public void setCode(String v) { this.code = v; }
    }

    // ── User: MFA status ─────────────────────────────────────────────────────

    public static class StatusResponse {
        private boolean enrolled;
        private MfaMethod method;
        private LocalDateTime enrolledAt;

        public boolean isEnrolled() { return enrolled; }
        public void setEnrolled(boolean v) { this.enrolled = v; }
        public MfaMethod getMethod() { return method; }
        public void setMethod(MfaMethod v) { this.method = v; }
        public LocalDateTime getEnrolledAt() { return enrolledAt; }
        public void setEnrolledAt(LocalDateTime v) { this.enrolledAt = v; }
    }

    // ── User: MFA verification (post-login) ──────────────────────────────────

    public static class VerifyRequest {
        @NotNull
        private String code;

        public String getCode() { return code; }
        public void setCode(String v) { this.code = v; }
    }

    public static class VerifyResponse {
        private boolean success;
        private String message;

        public VerifyResponse(boolean success, String message) {
            this.success = success; this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    // ── User: send OTP (for EMAIL/SMS) ────────────────────────────────────────

    public static class SendOtpResponse {
        private String message;
        public SendOtpResponse(String message) { this.message = message; }
        public String getMessage() { return message; }
    }
}
