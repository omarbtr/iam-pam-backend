package com.iam.pam.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iam.pam.dto.FaceDescriptorDTO;
import com.iam.pam.entity.UserFaceDescriptor;
import com.iam.pam.repository.FaceDescriptorRepository;
import com.iam.pam.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class FaceDescriptorService {

    private static final double THRESHOLD = 0.6;

    private final FaceDescriptorRepository faceRepo;
    private final ObjectMapper objectMapper;

    public FaceDescriptorService(FaceDescriptorRepository faceRepo) {
        this.faceRepo = faceRepo;
        this.objectMapper = new ObjectMapper();
    }

    public void enrollFace(String username, float[] descriptor) {
        String tenantId = TenantContext.getCurrentTenant();
        faceRepo.deleteByTenantIdAndUsername(tenantId, username);

        UserFaceDescriptor entity = new UserFaceDescriptor();
        entity.setTenantId(tenantId);
        entity.setUsername(username);
        entity.setDescriptorJson(toJson(descriptor));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setIsActive(true);
        faceRepo.save(entity);
    }

    @Transactional(readOnly = true)
    public FaceDescriptorDTO.StatusResponse getStatus(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        Optional<UserFaceDescriptor> opt = faceRepo.findByTenantIdAndUsernameAndIsActiveTrue(tenantId, username);
        FaceDescriptorDTO.StatusResponse status = new FaceDescriptorDTO.StatusResponse();
        status.setEnrolled(opt.isPresent());
        opt.ifPresent(d -> status.setEnrolledAt(d.getCreatedAt()));
        return status;
    }

    @Transactional(readOnly = true)
    public FaceDescriptorDTO.VerifyResponse verifyFace(String username, float[] inputDescriptor) {
        String tenantId = TenantContext.getCurrentTenant();
        Optional<UserFaceDescriptor> opt = faceRepo.findByTenantIdAndUsernameAndIsActiveTrue(tenantId, username);

        FaceDescriptorDTO.VerifyResponse result = new FaceDescriptorDTO.VerifyResponse();
        if (opt.isEmpty()) {
            result.setMatch(false);
            result.setDistance(1.0);
            return result;
        }

        float[] stored = fromJson(opt.get().getDescriptorJson());
        double distance = euclideanDistance(stored, inputDescriptor);
        result.setMatch(distance < THRESHOLD);
        result.setDistance(distance);
        return result;
    }

    public void removeEnrollment(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        faceRepo.deleteByTenantIdAndUsername(tenantId, username);
    }

    private double euclideanDistance(float[] a, float[] b) {
        double sum = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private String toJson(float[] descriptor) {
        try {
            return objectMapper.writeValueAsString(descriptor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize face descriptor", e);
        }
    }

    private float[] fromJson(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize face descriptor", e);
        }
    }
}
