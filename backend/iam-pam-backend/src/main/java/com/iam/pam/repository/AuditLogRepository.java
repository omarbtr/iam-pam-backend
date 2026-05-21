package com.iam.pam.repository;

import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.AuditLog.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    List<AuditLog> findAllByTenantIdOrderByTimestampDesc(String tenantId);

    List<AuditLog> findByUsernameAndTenantIdOrderByTimestampDesc(String username, String tenantId);

    List<AuditLog> findByActionAndTenantIdOrderByTimestampDesc(AuditAction action, String tenantId);

    List<AuditLog> findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
            String tenantId, LocalDateTime from, LocalDateTime to);

    long countByTenantId(String tenantId);

    long deleteByTenantIdAndTimestampBefore(String tenantId, LocalDateTime cutoff);

    List<AuditLog> findByTenantIdAndActionAndTimestampAfterOrderByTimestamp(
            String tenantId, AuditAction action, LocalDateTime since);

    List<AuditLog> findByTenantIdAndActionAndTimestampBetweenOrderByTimestampDesc(
            String tenantId, AuditAction action, LocalDateTime from, LocalDateTime to);

    @Query("SELECT a.resourceName, COUNT(a) FROM AuditLog a " +
           "WHERE a.tenantId = :tenantId AND a.action = :action AND a.resourceName IS NOT NULL " +
           "GROUP BY a.resourceName ORDER BY COUNT(a) DESC")
    List<Object[]> countSessionsByResource(@Param("tenantId") String tenantId,
                                           @Param("action") AuditAction action);
}
