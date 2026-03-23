package com.iam.pam.repository;

import com.iam.pam.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    // Trouver toutes les ressources d'un tenant
    List<Resource> findByTenantId(String tenantId);

    // Trouver les ressources actives d'un tenant
    List<Resource> findByTenantIdAndIsActiveTrue(String tenantId);

    // Trouver par nom et tenant
    Optional<Resource> findByNameAndTenantId(String name, String tenantId);

    // Trouver par type et tenant
    List<Resource> findByTypeAndTenantId(Resource.ResourceType type, String tenantId);

    // Verifier si une ressource existe pour un tenant
    boolean existsByNameAndTenantId(String name, String tenantId);
}