package com.iam.pam.service;

import com.iam.pam.dto.AuditLogDTO;
import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.AuditLog.AuditAction;
import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AccessRequest.RequestStatus;
import com.iam.pam.entity.Resource;
import com.iam.pam.repository.AccessRequestRepository;
import com.iam.pam.repository.AuditLogRepository;
import com.iam.pam.repository.ResourceRepository;
import com.iam.pam.security.TenantContext;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final ResourceRepository resourceRepository;

    public AuditService(AuditLogRepository auditLogRepository,
                        AccessRequestRepository accessRequestRepository,
                        ResourceRepository resourceRepository) {
        this.auditLogRepository = auditLogRepository;
        this.accessRequestRepository = accessRequestRepository;
        this.resourceRepository = resourceRepository;
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
    public Page<AuditLogDTO.Response> getFilteredLogs(int page, int size,
                                                       String username, String action,
                                                       String dateFrom, String dateTo) {
        String tenantId = TenantContext.getCurrentTenant();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("timestamp").descending());

        final String usernameFilter = (username == null || username.isBlank()) ? null : username.trim().toLowerCase();
        final AuditAction actionFilter = (action == null || action.isBlank()) ? null : AuditAction.valueOf(action);
        final LocalDateTime fromFilter = (dateFrom == null || dateFrom.isBlank()) ? null
                : LocalDate.parse(dateFrom).atStartOfDay();
        final LocalDateTime toFilter = (dateTo == null || dateTo.isBlank()) ? null
                : LocalDate.parse(dateTo).atTime(23, 59, 59);

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (usernameFilter != null) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + usernameFilter + "%"));
            }
            if (actionFilter != null) {
                predicates.add(cb.equal(root.get("action"), actionFilter));
            }
            if (fromFilter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), fromFilter));
            }
            if (toFilter != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), toFilter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageRequest).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        String tenantId = TenantContext.getCurrentTenant();
        long activeSessions = accessRequestRepository.countActiveSessions(tenantId, RequestStatus.APPROVED, LocalDateTime.now());

        LocalDateTime since = LocalDate.now().minusDays(6).atStartOfDay();
        List<AuditLog> sessionLogs = auditLogRepository
                .findByTenantIdAndActionAndTimestampAfterOrderByTimestamp(
                        tenantId, AuditAction.SESSION_STARTED, since);

        Map<String, Long> dayMap = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            dayMap.put(LocalDate.now().minusDays(i).toString(), 0L);
        }
        for (AuditLog entry : sessionLogs) {
            String day = entry.getTimestamp().toLocalDate().toString();
            dayMap.merge(day, 1L, Long::sum);
        }

        List<Map<String, Object>> sessionsByDay = dayMap.entrySet().stream()
                .map(e -> Map.<String, Object>of("date", e.getKey(), "count", e.getValue()))
                .collect(Collectors.toList());

        return Map.of("activeSessions", activeSessions, "sessionsByDay", sessionsByDay);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getResourceUsage() {
        String tenantId = TenantContext.getCurrentTenant();
        List<Object[]> rows = auditLogRepository.countSessionsByResource(tenantId, AuditAction.SESSION_STARTED);

        Map<String, String> nameToType = resourceRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Resource::getName, r -> r.getType().toString(), (a, b) -> a));

        return rows.stream().map(row -> {
            String name = (String) row[0];
            long count  = ((Number) row[1]).longValue();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("resourceName",  name);
            entry.put("resourceType",  nameToType.getOrDefault(name, "UNKNOWN"));
            entry.put("sessionCount",  count);
            return entry;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionsForDay(String dateStr) {
        String tenantId = TenantContext.getCurrentTenant();
        LocalDateTime from = LocalDate.parse(dateStr).atStartOfDay();
        LocalDateTime to   = from.plusDays(1);

        List<AuditLog> logs = auditLogRepository
                .findByTenantIdAndActionAndTimestampBetweenOrderByTimestampDesc(
                        tenantId, AuditAction.SESSION_STARTED, from, to);

        List<Long> ids = logs.stream()
                .filter(l -> l.getAccessRequestId() != null)
                .map(AuditLog::getAccessRequestId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, AccessRequest> reqMap = ids.isEmpty() ? Collections.emptyMap()
                : accessRequestRepository.findByIdsWithResource(ids).stream()
                        .collect(Collectors.toMap(AccessRequest::getId, Function.identity()));

        return logs.stream().map(l -> {
            AccessRequest req = l.getAccessRequestId() != null ? reqMap.get(l.getAccessRequestId()) : null;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sessionId",    l.getId());
            entry.put("requestId",    l.getAccessRequestId());
            entry.put("userName",     l.getUsername());
            entry.put("resourceName", l.getResourceName());
            entry.put("resourceType", req != null && req.getResource() != null
                    ? req.getResource().getType().toString() : "UNKNOWN");
            entry.put("startTime",    l.getTimestamp().toString());
            entry.put("durationHours", req != null ? req.getDurationHours() : null);
            entry.put("status",       req != null ? req.getStatus().toString() : "UNKNOWN");
            return entry;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO.Response> getUserLogs(String username) {
        String tenantId = TenantContext.getCurrentTenant();
        return auditLogRepository.findByUsernameAndTenantIdOrderByTimestampDesc(username, tenantId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String exportLogsAsCsv() {
        String tenantId = TenantContext.getCurrentTenant();
        List<AuditLog> logs = auditLogRepository.findAllByTenantIdOrderByTimestampDesc(tenantId);

        StringBuilder csv = new StringBuilder();
        // BOM UTF-8 pour compatibilité Excel (évite les caractères corrompus)
        csv.append('\uFEFF');
        // Séparateur ; pour les locales françaises (Excel FR attend ;)
        csv.append("ID;Timestamp;Username;Action;Resource;Details;IP;Result\r\n");
        for (AuditLog l : logs) {
            csv.append(escapeCsv(String.valueOf(l.getId()))).append(";");
            csv.append(escapeCsv(l.getTimestamp() != null ? l.getTimestamp().toString() : "")).append(";");
            csv.append(escapeCsv(l.getUsername())).append(";");
            csv.append(escapeCsv(l.getAction() != null ? l.getAction().toString() : "")).append(";");
            csv.append(escapeCsv(l.getResourceName())).append(";");
            csv.append(escapeCsv(l.getDetails())).append(";");
            csv.append(escapeCsv(l.getIpAddress())).append(";");
            csv.append(escapeCsv(l.getResult() != null ? l.getResult().toString() : "")).append("\r\n");
        }
        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
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