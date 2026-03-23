package com.iam.pam.service;

import com.iam.pam.dto.AccessRequestDTO;
import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AccessRequest.RequestStatus;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.Resource;
import com.iam.pam.repository.AccessRequestRepository;
import com.iam.pam.repository.ResourceRepository;
import com.iam.pam.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AccessRequestService {

    private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

    private final AccessRequestRepository accessRequestRepository;
    private final ResourceRepository resourceRepository;
    private final AuditService auditService;

    public AccessRequestService(AccessRequestRepository accessRequestRepository,
                                ResourceRepository resourceRepository,
                                AuditService auditService) {
        this.accessRequestRepository = accessRequestRepository;
        this.resourceRepository = resourceRepository;
        this.auditService = auditService;
    }

    public AccessRequestDTO.Response createRequest(AccessRequestDTO.Request dto, String username) {
        String tenantId = TenantContext.getCurrentTenant();

        Resource resource = resourceRepository.findById(dto.getResourceId())
                .orElseThrow(() -> new EntityNotFoundException("Resource not found"));

        if (!resource.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied: resource belongs to another tenant");
        }

        AccessRequest request = new AccessRequest();
        request.setRequesterUsername(username);
        request.setTenantId(tenantId);
        request.setResource(resource);
        request.setJustification(dto.getJustification());
        request.setDurationHours(dto.getDurationHours());
        request.setStatus(RequestStatus.PENDING);

        AccessRequest saved = accessRequestRepository.save(request);
        log.info("Access request created by {} for resource {} in tenant {}",
                username, resource.getName(), tenantId);

        auditService.log(AuditLog.AuditAction.ACCESS_REQUESTED, username, tenantId,
                resource.getName(), saved.getId(), "Access requested for " + resource.getName());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDTO.Response> getAllRequests() {
        String tenantId = TenantContext.getCurrentTenant();
        return accessRequestRepository.findByTenantIdOrderByRequestedAtDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDTO.Response> getMyRequests(String username) {
        return accessRequestRepository
                .findByRequesterUsernameOrderByRequestedAtDesc(username)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDTO.Response> getPendingRequests() {
        String tenantId = TenantContext.getCurrentTenant();
        return accessRequestRepository
                .findByTenantIdAndStatusOrderByRequestedAtDesc(tenantId, RequestStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AccessRequestDTO.Response reviewRequest(Long id,
                                                   AccessRequestDTO.ReviewRequest dto,
                                                   String reviewerUsername) {
        String tenantId = TenantContext.getCurrentTenant();

        AccessRequest request = accessRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"));

        if (!request.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied");
        }

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending");
        }

        request.setStatus(dto.getStatus());
        request.setReviewedBy(reviewerUsername);
        request.setReviewComment(dto.getComment());
        request.setReviewedAt(LocalDateTime.now());

        if (dto.getStatus() == RequestStatus.APPROVED) {
            request.setExpiresAt(
                    LocalDateTime.now().plusHours(request.getDurationHours())
            );
        }

        AccessRequest saved = accessRequestRepository.save(request);
        log.info("Request {} {} by {}", id, dto.getStatus(), reviewerUsername);

        AuditLog.AuditAction action = dto.getStatus() == RequestStatus.APPROVED
                ? AuditLog.AuditAction.ACCESS_APPROVED
                : AuditLog.AuditAction.ACCESS_REJECTED;

        auditService.log(action, reviewerUsername, tenantId,
                saved.getResource().getName(), saved.getId(),
                "Request " + dto.getStatus() + " by " + reviewerUsername);

        return toResponse(saved);
    }

    public AccessRequestDTO.Response revokeRequest(Long id, String adminUsername) {
        String tenantId = TenantContext.getCurrentTenant();

        AccessRequest request = accessRequestRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Request not found"));

        if (!request.getTenantId().equals(tenantId)) {
            throw new SecurityException("Access denied");
        }

        request.setStatus(RequestStatus.REVOKED);
        request.setReviewedBy(adminUsername);
        request.setReviewedAt(LocalDateTime.now());

        AccessRequest saved = accessRequestRepository.save(request);

        auditService.log(AuditLog.AuditAction.ACCESS_REVOKED, adminUsername, tenantId,
                saved.getResource().getName(), saved.getId(), "Access revoked by " + adminUsername);

        return toResponse(saved);
    }

    @Scheduled(fixedDelay = 60000)
    public void expireRequests() {
        List<AccessRequest> expired = accessRequestRepository.findExpiredRequests(LocalDateTime.now());

        expired.forEach(request -> {
            request.setStatus(RequestStatus.EXPIRED);
            accessRequestRepository.save(request);
            log.info("Request {} expired for user {}", request.getId(), request.getRequesterUsername());
        });

        if (!expired.isEmpty()) {
            log.info("{} access requests expired", expired.size());
        }
    }

    private AccessRequestDTO.Response toResponse(AccessRequest request) {
        AccessRequestDTO.Response response = new AccessRequestDTO.Response();
        response.setId(request.getId());
        response.setRequesterUsername(request.getRequesterUsername());
        response.setTenantId(request.getTenantId());
        response.setResourceId(request.getResource().getId());
        response.setResourceName(request.getResource().getName());
        response.setJustification(request.getJustification());
        response.setStatus(request.getStatus());
        response.setDurationHours(request.getDurationHours());
        response.setReviewedBy(request.getReviewedBy());
        response.setReviewComment(request.getReviewComment());
        response.setRequestedAt(request.getRequestedAt());
        response.setReviewedAt(request.getReviewedAt());
        response.setExpiresAt(request.getExpiresAt());
        response.setIsActive(request.isActive());
        return response;
    }
}



