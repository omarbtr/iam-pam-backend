package com.iam.pam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.pam.entity.UserFaceDescriptor;
import com.iam.pam.repository.FaceDescriptorRepository;
import com.iam.pam.service.FaceTokenService;
import com.iam.pam.service.KeycloakAdminService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/public/face")
public class FaceLoginController {

    private static final double THRESHOLD = 0.6;
    // Max age of the original token for a silent refresh (30 days).
    private static final long REFRESH_GRACE_MS = 30L * 24 * 3600 * 1000;

    private final FaceDescriptorRepository faceRepo;
    private final FaceTokenService faceTokenService;
    private final KeycloakAdminService keycloakAdminService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FaceLoginController(FaceDescriptorRepository faceRepo,
                               FaceTokenService faceTokenService,
                               KeycloakAdminService keycloakAdminService) {
        this.faceRepo = faceRepo;
        this.faceTokenService = faceTokenService;
        this.keycloakAdminService = keycloakAdminService;
    }

    @PostMapping("/login")
    public FaceLoginResponse login(@RequestBody FaceLoginRequest req) {
        List<UserFaceDescriptor> enrolled = faceRepo.findAllByUsernameAndIsActiveTrue(req.getUsername());
        if (enrolled.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Aucun visage enregistré pour cet utilisateur");
        }

        // Try each enrolled descriptor (user may have re-enrolled in a different tenant)
        UserFaceDescriptor best = null;
        double bestDistance = Double.MAX_VALUE;
        for (UserFaceDescriptor candidate : enrolled) {
            float[] stored = fromJson(candidate.getDescriptorJson());
            double dist = euclideanDistance(stored, req.getDescriptor());
            if (dist < bestDistance) {
                bestDistance = dist;
                best = candidate;
            }
        }

        if (best == null || bestDistance >= THRESHOLD) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Visage non reconnu (distance=" + String.format("%.3f", bestDistance) + ")");
        }

        String username = best.getUsername();
        String tenantId = best.getTenantId();
        List<String> roles = keycloakAdminService.getUserRolesByUsername(username);

        String token = faceTokenService.generateToken(username, roles, tenantId);
        return new FaceLoginResponse(token, username, roles, tenantId, bestDistance);
    }

    /**
     * Silent token refresh — called when the stored face JWT has expired.
     * Parses the expired token to extract identity, verifies the user still
     * has an active face enrollment, and issues a fresh 24 h token.
     * Allowed only within REFRESH_GRACE_MS of the original issuance.
     */
    @PostMapping("/refresh")
    public FaceLoginResponse refresh(@RequestBody FaceRefreshRequest req) {
        try {
            JWTClaimsSet claims = JWTParser.parse(req.getToken()).getJWTClaimsSet();

            // Grace-period check: issued within the last 30 days
            if (claims.getIssueTime() == null ||
                    System.currentTimeMillis() - claims.getIssueTime().getTime() > REFRESH_GRACE_MS) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Token trop ancien — veuillez vous reconnecter par Face ID");
            }

            String username = claims.getSubject();
            String tenantId = (String) claims.getClaim("tenantId");

            // Verify face enrollment is still active
            boolean enrolled = faceRepo.findAllByUsernameAndIsActiveTrue(username).stream()
                    .anyMatch(d -> tenantId == null || tenantId.equals(d.getTenantId()));
            if (!enrolled) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Enregistrement facial introuvable — reconnexion requise");
            }

            List<String> roles = keycloakAdminService.getUserRolesByUsername(username);
            String newToken = faceTokenService.generateToken(username, roles, tenantId);
            return new FaceLoginResponse(newToken, username, roles, tenantId, 0.0);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide");
        }
    }

    // ─── DTOs ──────────────────────────────────────────────────────────────

    public static class FaceLoginRequest {
        private String username;
        private float[] descriptor;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public float[] getDescriptor() { return descriptor; }
        public void setDescriptor(float[] descriptor) { this.descriptor = descriptor; }
    }

    public static class FaceRefreshRequest {
        private String token;
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class FaceLoginResponse {
        private String token;
        private String username;
        private List<String> roles;
        private String tenantId;
        private double distance;

        public FaceLoginResponse(String token, String username, List<String> roles, String tenantId, double distance) {
            this.token = token; this.username = username; this.roles = roles;
            this.tenantId = tenantId; this.distance = distance;
        }

        public String getToken() { return token; }
        public String getUsername() { return username; }
        public List<String> getRoles() { return roles; }
        public String getTenantId() { return tenantId; }
        public double getDistance() { return distance; }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private float[] fromJson(String json) {
        try { return objectMapper.readValue(json, float[].class); }
        catch (Exception e) { throw new RuntimeException("Failed to parse descriptor", e); }
    }

    private double euclideanDistance(float[] a, float[] b) {
        double sum = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) { double d = a[i] - b[i]; sum += d * d; }
        return Math.sqrt(sum);
    }
}
