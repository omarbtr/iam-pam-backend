package com.iam.pam.repository;

import com.iam.pam.entity.TenantMfaConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantMfaConfigRepository extends JpaRepository<TenantMfaConfig, Long> {
    Optional<TenantMfaConfig> findByTenantId(String tenantId);
}
