package com.iam.pam.service;

import com.iam.pam.dto.AuditLogDTO;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.AuditLog.AuditAction;
import com.iam.pam.repository.AuditLogRepository;
import com.iam.pam.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(AuditAction action, String username, String tenantId,
                    String resourceName, Long accessRequestId, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(action);
        auditLog.setUsername(username);
        auditLog.setTenantId(tenantId);
        auditLog.setResourceName(resourceName);
        auditLog.setAccessRequestId(accessRequestId);
        auditLog.setDetails(details);
        auditLog.setResult(AuditLog.AuditResult.SUCCESS);

        auditLogRepository.save(auditLog);
        log.debug("Audit log saved: {} by {} in {}", action, username, tenantId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDTO.Response> getLogs(int page, int size) {
        String tenantId = TenantContext.getCurrentTenant();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());

        return auditLogRepository.findByTenantIdOrderByTimestampDesc(tenantId, pageRequest)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO.Response> getUserLogs(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        return auditLogRepository.findByUsernameAndTenantIdOrderByTimestampDesc(username, tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private AuditLogDTO.Response toResponse(AuditLog log) {
        AuditLogDTO.Response response = new AuditLogDTO.Response();
        response.setId(log.getId());
        response.setAction(log.getAction());
        response.setUsername(log.getUsername());
        response.setTenantId(log.getTenantId());
        response.setResourceName(log.getResourceName());
        response.setAccessRequestId(log.getAccessRequestId());
        response.setDetails(log.getDetails());
        response.setIpAddress(log.getIpAddress());
        response.setResult(log.getResult());
        response.setTimestamp(log.getTimestamp());
        return response;
    }
}