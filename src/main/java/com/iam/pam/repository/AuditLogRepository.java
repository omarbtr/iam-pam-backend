package com.iam.pam.repository;

import com.iam.pam.entity.AuditLog;
import com.iam.pam.entity.AuditLog.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Logs d'un tenant avec pagination
    Page<AuditLog> findByTenantIdOrderByTimestampDesc(String tenantId, Pageable pageable);

    // Logs d'un utilisateur
    List<AuditLog> findByUsernameAndTenantIdOrderByTimestampDesc(
            String username, String tenantId);

    // Logs par action
    List<AuditLog> findByActionAndTenantIdOrderByTimestampDesc(
            AuditAction action, String tenantId);

    // Logs dans une periode
    List<AuditLog> findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
            String tenantId, LocalDateTime from, LocalDateTime to);

    // Compter les logs d'un tenant
    long countByTenantId(String tenantId);
}
