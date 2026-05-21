package com.iam.pam.repository;

import com.iam.pam.entity.RdpAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RdpAuditLogRepository extends JpaRepository<RdpAuditLog, Long> {

    List<RdpAuditLog> findByAccessRequestIdOrderByOccurredAtAsc(Long accessRequestId);

    List<RdpAuditLog> findByTenantIdOrderByOccurredAtDesc(String tenantId);

    long deleteByTenantIdAndOccurredAtBefore(String tenantId, LocalDateTime cutoff);
}
