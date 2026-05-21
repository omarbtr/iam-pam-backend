package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "access_request_id")
    private Long accessRequestId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address")
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private AuditResult result = AuditResult.SUCCESS;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    // CONSTRUCTEURS
    public AuditLog() {}

    public AuditLog(Long id, AuditAction action, String username, String tenantId,
                    String resourceName, Long accessRequestId, String details,
                    String ipAddress, AuditResult result, LocalDateTime timestamp) {
        this.id = id;
        this.action = action;
        this.username = username;
        this.tenantId = tenantId;
        this.resourceName = resourceName;
        this.accessRequestId = accessRequestId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.result = result;
        this.timestamp = timestamp;
    }

    // GETTERS
    public Long getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getUsername() {
        return username;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Long getAccessRequestId() {
        return accessRequestId;
    }

    public String getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public AuditResult getResult() {
        return result;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    // SETTERS
    public void setId(Long id) {
        this.id = id;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public void setAccessRequestId(Long accessRequestId) {
        this.accessRequestId = accessRequestId;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setResult(AuditResult result) {
        this.result = result;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // ENUMS
    public enum AuditAction {
        USER_LOGIN,
        USER_LOGOUT,
        ACCESS_REQUESTED,
        ACCESS_APPROVED,
        ACCESS_REJECTED,
        ACCESS_REVOKED,
        ACCESS_EXPIRED,
        RESOURCE_CREATED,
        RESOURCE_UPDATED,
        RESOURCE_DELETED,
        SESSION_STARTED,
        SESSION_ENDED,
        COMMAND_EXECUTED,
        PERMISSION_DENIED
    }

    public enum AuditResult {
        SUCCESS,
        FAILURE
    }
}