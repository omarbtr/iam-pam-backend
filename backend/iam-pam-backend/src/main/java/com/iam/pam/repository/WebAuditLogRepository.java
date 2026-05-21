package com.iam.pam.repository;

import com.iam.pam.entity.WebAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WebAuditLogRepository extends JpaRepository<WebAuditLog, Long> {

    List<WebAuditLog> findByAccessRequestIdOrderByOccurredAtAsc(Long accessRequestId);

    List<WebAuditLog> findByTenantIdOrderByOccurredAtDesc(String tenantId);

    long deleteByTenantIdAndOccurredAtBefore(String tenantId, LocalDateTime cutoff);
}
