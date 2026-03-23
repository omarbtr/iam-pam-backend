package com.iam.pam.repository;

import com.iam.pam.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    Optional<Tenant> findBySchemaName(String schemaName);

    boolean existsByDomain(String domain);

    List<Tenant> findByIsActiveTrue();
}
