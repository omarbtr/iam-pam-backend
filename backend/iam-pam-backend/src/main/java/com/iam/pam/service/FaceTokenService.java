package com.iam.pam.service;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class FaceTokenService {

    @Value("${face.jwt.secret}")
    private String secret;

    @Value("${face.jwt.expiry-hours:24}")
    private int expiryHours;

    public String generateToken(String username, List<String> roles, String tenantId) {
        try {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            // Pad or truncate to 32 bytes for HS256
            byte[] key = new byte[32];
            System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));

            JWSSigner signer = new MACSigner(key);

            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issuer("face-auth")
                    .claim("preferred_username", username)
                    .claim("roles", roles)
                    .claim("tenantId", tenantId)
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + (long) expiryHours * 3600_000));

            // Add "groups" claim in Keycloak format so TenantInterceptor picks it up
            if (tenantId != null && !tenantId.isBlank()) {
                builder.claim("groups", java.util.List.of(tenantId));
            }

            JWTClaimsSet claims = builder.build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate face JWT", e);
        }
    }
}
