package com.iam.pam.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM JPA converter — transparently encrypts/decrypts credential fields.
 * Key is read from ${resource.encryption.key} (env: RESOURCE_ENCRYPTION_KEY).
 * IV is prepended to the ciphertext so each value has a unique IV.
 */
@Converter
@Component
public class AesEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM    = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH    = 12;
    private static final int    TAG_BITS     = 128;
    private static final int    KEY_BYTES    = 32;

    // Static field so the value is accessible even when Hibernate creates
    // its own converter instance (before Spring injects into it).
    private static volatile byte[] secretKey;

    @Value("${resource.encryption.key:default-key-change-in-prod-32chars!}")
    public void setEncryptionKey(String key) {
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        secretKey = Arrays.copyOf(raw, KEY_BYTES); // pad/truncate to 32 bytes
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Credential encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            byte[] combined  = Base64.getDecoder().decode(ciphertext);
            byte[] iv        = Arrays.copyOf(combined, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Credential decryption failed", e);
        }
    }

    private static SecretKeySpec key() {
        byte[] k = secretKey;
        if (k == null) {
            // Fallback when Hibernate creates the converter before Spring injects the key
            k = Arrays.copyOf(
                "default-key-change-in-prod-32chars!".getBytes(StandardCharsets.UTF_8),
                KEY_BYTES);
        }
        return new SecretKeySpec(k, "AES");
    }
}
