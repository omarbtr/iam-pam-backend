package com.iam.pam.repository;

import com.iam.pam.entity.AccessRequest;
import com.iam.pam.entity.AccessRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    // Demandes d'un utilisateur
    List<AccessRequest> findByRequesterUsernameOrderByRequestedAtDesc(String username);

    // Sessions actives d'un utilisateur (APPROVED)
    List<AccessRequest> findByRequesterUsernameAndStatusOrderByRequestedAtDesc(
            String username, RequestStatus status);

    // Demandes d'un tenant
    List<AccessRequest> findByTenantIdOrderByRequestedAtDesc(String tenantId);

    // Demandes par statut
    List<AccessRequest> findByStatusOrderByRequestedAtDesc(RequestStatus status);

    // Demandes en attente d'un tenant
    List<AccessRequest> findByTenantIdAndStatusOrderByRequestedAtDesc(
            String tenantId, RequestStatus status);

    // Demandes actives expirees
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.status = :status AND ar.expiresAt < :now")
    List<AccessRequest> findExpiredRequests(@org.springframework.data.repository.query.Param("now") LocalDateTime now,
                                            @org.springframework.data.repository.query.Param("status") RequestStatus status);

    // Toutes les demandes approuvées (toutes tenants) pour le job d'expiry
    List<AccessRequest> findByStatus(RequestStatus status);

    long countByTenantIdAndStatus(String tenantId, RequestStatus status);

    @Query("SELECT COUNT(ar) FROM AccessRequest ar WHERE ar.tenantId = :tenantId " +
           "AND ar.status = :status AND (ar.expiresAt IS NULL OR ar.expiresAt > :now)")
    long countActiveSessions(@Param("tenantId") String tenantId,
                             @Param("status") RequestStatus status,
                             @Param("now") LocalDateTime now);

    @Query("SELECT ar FROM AccessRequest ar JOIN FETCH ar.resource WHERE ar.id = :id")
    Optional<AccessRequest> findByIdWithResource(@Param("id") Long id);

    @Query("SELECT ar FROM AccessRequest ar JOIN FETCH ar.resource WHERE ar.id IN :ids")
    List<AccessRequest> findByIdsWithResource(@Param("ids") List<Long> ids);
}