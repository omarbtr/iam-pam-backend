package com.iam.pam.repository;

import com.iam.pam.entity.TenantAdConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantAdConfigRepository extends JpaRepository<TenantAdConfig, Long> {
    Optional<TenantAdConfig> findByTenantId(String tenantId);
    boolean existsByTenantId(String tenantId);
}
