package com.iam.pam.repository;

import com.iam.pam.entity.UserMfaEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserMfaEnrollmentRepository extends JpaRepository<UserMfaEnrollment, Long> {

    Optional<UserMfaEnrollment> findByTenantIdAndUsername(String tenantId, String username);

    @Modifying
    @Query("DELETE FROM UserMfaEnrollment e WHERE e.tenantId = :tenantId AND e.username = :username")
    void deleteByTenantIdAndUsername(@Param("tenantId") String tenantId, @Param("username") String username);
}
