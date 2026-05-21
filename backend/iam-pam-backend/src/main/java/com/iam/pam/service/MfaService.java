package com.iam.pam.service;

import com.iam.pam.dto.MfaDTO;
import com.iam.pam.entity.TenantMfaConfig;
import com.iam.pam.entity.UserMfaEnrollment;
import com.iam.pam.entity.UserMfaEnrollment.MfaMethod;
import com.iam.pam.repository.TenantMfaConfigRepository;
import com.iam.pam.repository.UserMfaEnrollmentRepository;
import com.iam.pam.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);
    private static final String ISSUER = "IAM-PAM";
    private static final int OTP_TTL_SECONDS = 300; // 5 minutes

    private final TenantMfaConfigRepository tenantMfaConfigRepo;
    private final UserMfaEnrollmentRepository enrollmentRepo;
    private final NotificationService notificationService;

    @Value("${spring.application.name:IAM-PAM}")
    private String appName;

    // In-memory OTP store: key = "tenantId:username", value = [code, expireEpoch]
    private final Map<String, long[]> otpStore = new ConcurrentHashMap<>();
    // key = "tenantId:username" → pending TOTP secret during enrollment
    private final Map<String, String> pendingTotpSecrets = new ConcurrentHashMap<>();

    public MfaService(TenantMfaConfigRepository tenantMfaConfigRepo,
                      UserMfaEnrollmentRepository enrollmentRepo,
                      NotificationService notificationService) {
        this.tenantMfaConfigRepo = tenantMfaConfigRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.notificationService = notificationService;
    }

    // ── Tenant-admin: MFA configuration ─────────────────────────────────────

    @Transactional(readOnly = true)
    public MfaDTO.TenantConfigResponse getTenantConfig() {
        String tenantId = TenantContext.getCurrentTenant();
        TenantMfaConfig cfg = tenantMfaConfigRepo.findByTenantId(tenantId)
                .orElseGet(() -> new TenantMfaConfig(tenantId));
        return toConfigResponse(cfg);
    }

    public MfaDTO.TenantConfigResponse updateTenantConfig(MfaDTO.TenantConfigRequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        TenantMfaConfig cfg = tenantMfaConfigRepo.findByTenantId(tenantId)
                .orElseGet(() -> new TenantMfaConfig(tenantId));

        if (req.getTotpEnabled()         != null) cfg.setTotpEnabled(req.getTotpEnabled());
        if (req.getEmailOtpEnabled()     != null) cfg.setEmailOtpEnabled(req.getEmailOtpEnabled());
        if (req.getSmsOtpEnabled()       != null) cfg.setSmsOtpEnabled(req.getSmsOtpEnabled());
        if (req.getWhatsappOtpEnabled()  != null) cfg.setWhatsappOtpEnabled(req.getWhatsappOtpEnabled());
        if (req.getMfaRequired()         != null) cfg.setMfaRequired(req.getMfaRequired());

        return toConfigResponse(tenantMfaConfigRepo.save(cfg));
    }

    // ── User: enrollment ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MfaDTO.StatusResponse getStatus(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        Optional<UserMfaEnrollment> opt = enrollmentRepo.findByTenantIdAndUsername(tenantId, username);
        MfaDTO.StatusResponse r = new MfaDTO.StatusResponse();
        opt.ifPresent(e -> {
            r.setEnrolled(Boolean.TRUE.equals(e.getIsActive()));
            r.setMethod(e.getMethod());
            r.setEnrolledAt(e.getEnrolledAt());
        });
        return r;
    }

    public MfaDTO.InitEnrollResponse initEnroll(String username, MfaDTO.InitEnrollRequest req) {
        String tenantId = TenantContext.getCurrentTenant();

        // Validate method is allowed for this tenant
        TenantMfaConfig cfg = tenantMfaConfigRepo.findByTenantId(tenantId)
                .orElseGet(() -> new TenantMfaConfig(tenantId));
        validateMethodAllowed(req.getMethod(), cfg);

        // Delete any previous pending enrollment
        enrollmentRepo.deleteByTenantIdAndUsername(tenantId, username);

        UserMfaEnrollment enrollment = new UserMfaEnrollment();
        enrollment.setTenantId(tenantId);
        enrollment.setUsername(username);
        enrollment.setMethod(req.getMethod());
        enrollment.setIsActive(false); // not active until confirmed

        MfaDTO.InitEnrollResponse resp = new MfaDTO.InitEnrollResponse();
        resp.setMethod(req.getMethod());
        String storeKey = tenantId + ":" + username;

        switch (req.getMethod()) {
            case TOTP -> {
                String secret = generateBase32Secret();
                pendingTotpSecrets.put(storeKey, secret);
                enrollment.setSecret(secret);
                String qrUri = buildTotpUri(username, secret);
                resp.setTotpSecret(secret);
                resp.setTotpQrUri(qrUri);
                resp.setMessage("Scannez le QR code avec votre application d'authentification puis entrez le code affiché.");
            }
            case EMAIL -> {
                if (req.getContactEmail() == null || req.getContactEmail().isBlank())
                    throw new IllegalArgumentException("Email requis pour la méthode EMAIL");
                enrollment.setContactEmail(req.getContactEmail());
                String code = generateNumericOtp();
                storeOtp(storeKey, code);
                sendEmailOtp(req.getContactEmail(), code);
                resp.setMessage("Un code a été envoyé à " + req.getContactEmail() + ". Saisissez-le pour confirmer.");
            }
            case SMS -> {
                if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank())
                    throw new IllegalArgumentException("Numéro de téléphone requis pour la méthode SMS");
                enrollment.setPhoneNumber(req.getPhoneNumber());
                String code = generateNumericOtp();
                storeOtp(storeKey, code);
                sendSmsOtp(req.getPhoneNumber(), code);
                resp.setMessage("Un code a été envoyé au " + req.getPhoneNumber() + ". Saisissez-le pour confirmer.");
            }
            case WHATSAPP -> {
                if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank())
                    throw new IllegalArgumentException("Numéro WhatsApp requis pour la méthode WHATSAPP");
                enrollment.setPhoneNumber(req.getPhoneNumber());
                String code = generateNumericOtp();
                storeOtp(storeKey, code);
                sendWhatsAppOtp(req.getPhoneNumber(), code);
                resp.setMessage("Un code a été envoyé sur WhatsApp au " + req.getPhoneNumber() + ". Saisissez-le pour confirmer.");
            }
        }

        enrollmentRepo.save(enrollment);
        return resp;
    }

    public void confirmEnroll(String username, MfaDTO.ConfirmEnrollRequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        String storeKey = tenantId + ":" + username;

        UserMfaEnrollment enrollment = enrollmentRepo.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new IllegalStateException("Aucune inscription en cours. Recommencez l'enregistrement."));

        boolean valid = switch (enrollment.getMethod()) {
            case TOTP              -> verifyTotp(enrollment.getSecret(), req.getCode());
            case EMAIL, SMS,
                 WHATSAPP          -> verifyStoredOtp(storeKey, req.getCode());
        };

        if (!valid) throw new IllegalArgumentException("Code invalide ou expiré.");

        enrollment.setIsActive(true);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollmentRepo.save(enrollment);
        pendingTotpSecrets.remove(storeKey);
        otpStore.remove(storeKey);

        log.info("MFA enrollment confirmed for user {} (method={}) in tenant {}", username, enrollment.getMethod(), tenantId);
    }

    public void removeEnrollment(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        enrollmentRepo.deleteByTenantIdAndUsername(tenantId, username);
        log.info("MFA enrollment removed for user {} in tenant {}", username, tenantId);
    }

    // ── Post-login MFA verification ──────────────────────────────────────────

    public MfaDTO.SendOtpResponse sendVerificationOtp(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        UserMfaEnrollment enrollment = enrollmentRepo.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur non enregistré au MFA."));

        String storeKey = tenantId + ":" + username;
        return switch (enrollment.getMethod()) {
            case EMAIL -> {
                String code = generateNumericOtp();
                storeOtp(storeKey, code);
                sendEmailOtp(enrollment.getContactEmail(), code);
                yield new MfaDTO.SendOtpResponse("Code envoyé à " + enrollment.getContactEmail());
            }
            case SMS -> {
                String code = generateNumericOtp();
                storeOtp(storeKey, code);
                sendSmsOtp(enrollment.getPhoneNumber(), code);
                yield new MfaDTO.SendOtpResponse("Code envoyé au " + enrollment.getPhoneNumber());
            }
            case WHATSAPP -> {
                String code = generateNumericOtp();
                storeOtp(storeKey, code);
                sendWhatsAppOtp(enrollment.getPhoneNumber(), code);
                yield new MfaDTO.SendOtpResponse("Code envoyé sur WhatsApp au " + enrollment.getPhoneNumber());
            }
            case TOTP -> new MfaDTO.SendOtpResponse("Ouvrez votre application TOTP pour obtenir le code.");
        };
    }

    public MfaDTO.VerifyResponse verifyMfa(String username, MfaDTO.VerifyRequest req) {
        String tenantId = TenantContext.getCurrentTenant();
        String storeKey = tenantId + ":" + username;

        UserMfaEnrollment enrollment = enrollmentRepo.findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new IllegalStateException("Utilisateur non enregistré au MFA."));

        if (!Boolean.TRUE.equals(enrollment.getIsActive())) {
            return new MfaDTO.VerifyResponse(false, "MFA non configuré. Veuillez d'abord l'activer.");
        }

        boolean valid = switch (enrollment.getMethod()) {
            case TOTP              -> verifyTotp(enrollment.getSecret(), req.getCode());
            case EMAIL, SMS,
                 WHATSAPP          -> verifyStoredOtp(storeKey, req.getCode());
        };

        if (valid) {
            enrollment.setLastVerifiedAt(LocalDateTime.now());
            enrollmentRepo.save(enrollment);
            otpStore.remove(storeKey);
        }

        return new MfaDTO.VerifyResponse(valid, valid ? "Vérification réussie." : "Code invalide ou expiré.");
    }

    // ── TOTP implementation (RFC 6238) ────────────────────────────────────────

    private String generateBase32Secret() {
        byte[] bytes = new byte[10]; // 80 bits
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    private String buildTotpUri(String username, String secret) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                encode(ISSUER), encode(username), secret, encode(ISSUER));
    }

    boolean verifyTotp(String base32Secret, String code) {
        if (code == null || code.length() != 6) return false;
        try {
            byte[] key = base32Decode(base32Secret);
            long T = System.currentTimeMillis() / 1000 / 30;
            // Accept T-1, T, T+1 for clock drift
            for (long t = T - 1; t <= T + 1; t++) {
                if (generateTotpCode(key, t).equals(code)) return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("TOTP verification error: {}", e.getMessage());
            return false;
        }
    }

    private String generateTotpCode(byte[] key, long counter) throws Exception {
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }

    // ── OTP helpers ───────────────────────────────────────────────────────────

    private String generateNumericOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private void storeOtp(String key, String code) {
        long expire = System.currentTimeMillis() + (long) OTP_TTL_SECONDS * 1000;
        // Store hash of code to avoid plaintext in memory
        otpStore.put(key, new long[]{ (long) code.hashCode(), expire });
    }

    private boolean verifyStoredOtp(String key, String code) {
        long[] entry = otpStore.get(key);
        if (entry == null) return false;
        if (entry[1] < System.currentTimeMillis()) {
            otpStore.remove(key);
            return false;
        }
        return entry[0] == (long) code.hashCode();
    }

    private void sendEmailOtp(String email, String code) {
        notificationService.sendEmailOtp(email, code);
    }

    private void sendSmsOtp(String phone, String code) {
        notificationService.sendSmsOtp(phone, code);
    }

    private void sendWhatsAppOtp(String phone, String code) {
        notificationService.sendWhatsAppOtp(phone, code);
    }

    // ── BASE32 helpers ────────────────────────────────────────────────────────

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                sb.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 31));
            }
        }
        if (bitsLeft > 0) sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 31));
        return sb.toString();
    }

    private byte[] base32Decode(String encoded) {
        encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] out = new byte[encoded.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : encoded.toCharArray()) {
            buffer = (buffer << 5) | BASE32_CHARS.indexOf(c);
            bitsLeft += 5;
            if (bitsLeft >= 8) { bitsLeft -= 8; out[idx++] = (byte) ((buffer >> bitsLeft) & 0xFF); }
        }
        return out;
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateMethodAllowed(MfaMethod method, TenantMfaConfig cfg) {
        boolean allowed = switch (method) {
            case TOTP     -> Boolean.TRUE.equals(cfg.getTotpEnabled());
            case EMAIL    -> Boolean.TRUE.equals(cfg.getEmailOtpEnabled());
            case SMS      -> Boolean.TRUE.equals(cfg.getSmsOtpEnabled());
            case WHATSAPP -> Boolean.TRUE.equals(cfg.getWhatsappOtpEnabled());
        };
        if (!allowed) throw new IllegalArgumentException("La méthode " + method + " n'est pas activée pour ce tenant.");
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private MfaDTO.TenantConfigResponse toConfigResponse(TenantMfaConfig cfg) {
        MfaDTO.TenantConfigResponse r = new MfaDTO.TenantConfigResponse();
        r.setTenantId(cfg.getTenantId());
        r.setTotpEnabled(Boolean.TRUE.equals(cfg.getTotpEnabled()));
        r.setEmailOtpEnabled(Boolean.TRUE.equals(cfg.getEmailOtpEnabled()));
        r.setSmsOtpEnabled(Boolean.TRUE.equals(cfg.getSmsOtpEnabled()));
        r.setWhatsappOtpEnabled(Boolean.TRUE.equals(cfg.getWhatsappOtpEnabled()));
        r.setMfaRequired(Boolean.TRUE.equals(cfg.getMfaRequired()));
        return r;
    }
}
