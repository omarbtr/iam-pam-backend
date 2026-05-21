package com.iam.pam.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Handles Email OTP (SMTP), SMS OTP (Twilio Messages), and WhatsApp OTP (Twilio Messages).
 * Each channel gracefully falls back to log-only when its credentials are not configured,
 * so the app stays functional in dev/demo without real credentials.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // ── Email ──────────────────────────────────────────────────────────────────

    @Nullable
    private final JavaMailSender mailSender;

    @Value("${mfa.email.from:noreply@iam-pam.local}")
    private String emailFrom;

    @Value("${mfa.email.enabled:false}")
    private boolean emailEnabled;

    // ── Twilio (SMS + WhatsApp) ────────────────────────────────────────────────

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.sms.from:}")
    private String smsSenderNumber;

    @Value("${twilio.whatsapp.from:whatsapp:+14155238886}")
    private String whatsappSenderNumber;

    @Value("${twilio.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${twilio.whatsapp.enabled:false}")
    private boolean whatsappEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public NotificationService(@Nullable JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ── Public send methods ────────────────────────────────────────────────────

    public void sendEmailOtp(String to, String code) {
        if (!emailEnabled || mailSender == null || emailFrom == null || emailFrom.isBlank()) {
            log.info("📧 [EMAIL OTP — log-only, set MFA_EMAIL_ENABLED=true + SMTP credentials] To: {} | Code: {}", to, code);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(to);
            helper.setSubject("Votre code de vérification IAM-PAM");
            helper.setText(buildEmailBody(code), true);
            mailSender.send(msg);
            log.info("📧 [EMAIL OTP] Sent to {}", to);
        } catch (MessagingException e) {
            log.error("📧 [EMAIL OTP] Failed to send to {}: {} — falling back to log | Code: {}",
                    to, e.getMessage(), code);
        }
    }

    public void sendSmsOtp(String to, String code) {
        if (twilioAccountSid.isBlank() || twilioAuthToken.isBlank()) {
            log.warn("📱 *** SMS OTP (Twilio not configured) *** To: {} | Code: {}", to, code);
            return;
        }
        String normalized = normalizePhone(to);
        try {
            sendTwilioMessage(smsSenderNumber, normalized, "Votre code IAM-PAM : " + code);
            log.info("📱 [SMS OTP] Sent to {} (normalized from {})", normalized, to);
        } catch (Exception e) {
            log.error("📱 [SMS OTP] Failed to send to {} (normalized: {}): {} | Code: {}",
                    to, normalized, e.getMessage(), code);
        }
    }

    public void sendWhatsAppOtp(String to, String code) {
        if (twilioAccountSid.isBlank() || twilioAuthToken.isBlank()) {
            log.warn("💬 *** WHATSAPP OTP (Twilio not configured) *** To: {} | Code: {}", to, code);
            return;
        }
        // Normalize to E.164 first, then add the "whatsapp:" prefix Twilio requires
        String normalized = normalizePhone(to);
        String whatsappTo = normalized.startsWith("whatsapp:") ? normalized : "whatsapp:" + normalized;
        try {
            sendTwilioMessage(whatsappSenderNumber, whatsappTo, "Votre code IAM-PAM : " + code);
            log.info("💬 [WHATSAPP OTP] Sent to {} (normalized from {})", whatsappTo, to);
        } catch (Exception e) {
            log.error("💬 [WHATSAPP OTP] Failed to send to {} (normalized: {}): {} | Code: {}",
                    to, whatsappTo, e.getMessage(), code);
        }
    }

    // ── Phone normalization (E.164) ────────────────────────────────────────────

    /**
     * Normalizes a phone number to E.164 format for Twilio.
     *
     * Rules (applied in order):
     *  1. Strip all spaces, dashes, dots and parentheses
     *  2. If the number already starts with '+' → keep as-is (already E.164)
     *  3. If it starts with '00' → replace with '+'
     *  4. Otherwise → prepend '+216' (Tunisia default for this deployment)
     *
     * Examples:
     *   "28 125 264"     → "+21628125264"
     *   "+216 28 125 264" → "+21628125264"
     *   "0021628125264"  → "+21628125264"
     *   "+33612345678"   → "+33612345678"  (French number kept as-is)
     */
    static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        // Remove formatting characters
        String cleaned = raw.replaceAll("[\\s\\-.()/]", "");
        if (cleaned.startsWith("+")) return cleaned;
        if (cleaned.startsWith("00"))  return "+" + cleaned.substring(2);
        return "+216" + cleaned;
    }

    // ── Twilio Messages API ────────────────────────────────────────────────────

    private void sendTwilioMessage(String from, String to, String body) {
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String credentials = Base64.getEncoder()
                .encodeToString((twilioAccountSid + ":" + twilioAuthToken)
                        .getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + credentials);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To",   to);
        form.add("Body", body);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(form, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Twilio returned HTTP " + response.getStatusCode()
                    + ": " + response.getBody());
        }
    }

    // ── Email HTML body ────────────────────────────────────────────────────────

    private String buildEmailBody(String code) {
        return """
                <div style="font-family:sans-serif;max-width:480px;margin:auto;padding:32px">
                  <h2 style="color:#0f172a;margin-bottom:8px">Vérification IAM-PAM</h2>
                  <p style="color:#64748b;font-size:14px">Utilisez ce code pour confirmer votre identité :</p>
                  <div style="background:#f1f5f9;border-radius:12px;padding:24px;text-align:center;
                              font-size:36px;font-weight:700;letter-spacing:8px;color:#0f172a;
                              font-family:monospace;margin:24px 0">
                    %s
                  </div>
                  <p style="color:#94a3b8;font-size:12px">
                    Ce code expire dans 5 minutes. Ne le partagez avec personne.
                  </p>
                </div>
                """.formatted(code);
    }
}
