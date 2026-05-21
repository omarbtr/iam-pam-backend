package com.iam.pam.repository;

import com.iam.pam.entity.UserFaceDescriptor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FaceDescriptorRepository extends JpaRepository<UserFaceDescriptor, Long> {

    Optional<UserFaceDescriptor> findByTenantIdAndUsernameAndIsActiveTrue(String tenantId, String username);

    // Cross-tenant lookup for face login (user may belong to any tenant)
    List<UserFaceDescriptor> findAllByUsernameAndIsActiveTrue(String username);

    @Modifying
    @Query("DELETE FROM UserFaceDescriptor f WHERE f.tenantId = :tenantId AND f.username = :username")
    void deleteByTenantIdAndUsername(@Param("tenantId") String tenantId, @Param("username") String username);
}
