package com.iam.pam.repository;

import com.iam.pam.entity.Tenant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tenant t WHERE t.tenantId = :tenantId")
    Optional<Tenant> findByTenantIdForUpdate(@Param("tenantId") String tenantId);

    boolean existsByTenantId(String tenantId);

    Optional<Tenant> findBySchemaName(String schemaName);

    boolean existsByDomain(String domain);

    List<Tenant> findByIsActiveTrue();
}
