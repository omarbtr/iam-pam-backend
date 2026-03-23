package com.iam.pam.repository;

import com.iam.pam.entity.TenantService;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;

public interface TenantServiceRepository extends JpaRepository<TenantService, Long> {
    Optional<TenantService> findByTenantIdAndServiceTypeAndIsActiveTrue(String tenantId, TenantService.ServiceType serviceType);
    List<TenantService> findByTenantIdAndIsActiveTrue(String tenantId);
    List<TenantService> findByTenantId(String tenantId);
}