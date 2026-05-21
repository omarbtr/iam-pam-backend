package com.iam.pam.service;

import com.iam.pam.entity.Tenant;
import com.iam.pam.repository.AuditLogRepository;
import com.iam.pam.repository.RdpAuditLogRepository;
import com.iam.pam.repository.TenantRepository;
import com.iam.pam.repository.WebAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Supprime chaque nuit les journaux d'audit plus anciens que la période
 * configurée par chaque tenant (7, 14 ou 30 jours).
 */
@Service
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    private final TenantRepository tenantRepository;
    private final AuditLogRepository auditLogRepository;
    private final RdpAuditLogRepository rdpAuditLogRepository;
    private final WebAuditLogRepository webAuditLogRepository;

    public AuditRetentionService(TenantRepository tenantRepository,
                                  AuditLogRepository auditLogRepository,
                                  RdpAuditLogRepository rdpAuditLogRepository,
                                  WebAuditLogRepository webAuditLogRepository) {
        this.tenantRepository = tenantRepository;
        this.auditLogRepository = auditLogRepository;
        this.rdpAuditLogRepository = rdpAuditLogRepository;
        this.webAuditLogRepository = webAuditLogRepository;
    }

    /** Runs every night at 02:00 */
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyCleanup() {
        log.info("Audit retention cleanup started");
        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            applyRetention(tenant.getTenantId(), tenant.getAuditLogRetentionDays());
        }
        log.info("Audit retention cleanup finished ({} tenants processed)", tenants.size());
    }

    /** Immediately purge logs older than {@code days} for the given tenant. */
    @Transactional
    public void applyRetention(String tenantId, int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        long a = auditLogRepository.deleteByTenantIdAndTimestampBefore(tenantId, cutoff);
        long r = rdpAuditLogRepository.deleteByTenantIdAndOccurredAtBefore(tenantId, cutoff);
        long w = webAuditLogRepository.deleteByTenantIdAndOccurredAtBefore(tenantId, cutoff);
        if (a + r + w > 0) {
            log.info("Tenant {}: deleted {} audit + {} RDP + {} web logs older than {} days",
                    tenantId, a, r, w, days);
        }
    }
}
