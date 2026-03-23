package com.iam.pam.repository;

import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AccessRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    // Demandes d'un utilisateur
    List<AccessRequest> findByRequesterUsernameOrderByRequestedAtDesc(String username);

    // Demandes d'un tenant
    List<AccessRequest> findByTenantIdOrderByRequestedAtDesc(String tenantId);

    // Demandes par statut
    List<AccessRequest> findByStatusOrderByRequestedAtDesc(RequestStatus status);

    // Demandes en attente d'un tenant
    List<AccessRequest> findByTenantIdAndStatusOrderByRequestedAtDesc(
            String tenantId, RequestStatus status);

    // Demandes actives expirees
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.status = 'APPROVED' AND ar.expiresAt < :now")
    List<AccessRequest> findExpiredRequests(LocalDateTime now);

    // Compter les demandes en attente d'un tenant
    long countByTenantIdAndStatus(String tenantId, RequestStatus status);
}