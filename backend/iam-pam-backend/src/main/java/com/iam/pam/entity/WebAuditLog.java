package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "web_audit_logs")
public class WebAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_request_id", nullable = false)
    private Long accessRequestId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "resource_name")
    private String resourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    public enum EventType {
        SESSION_START,
        SESSION_END,
        PAGE_VISIT,
        AUTO_LOGIN_SUCCESS,
        AUTO_LOGIN_FAILURE,
        FORM_SUBMIT
    }

    public WebAuditLog() {}

    public WebAuditLog(Long accessRequestId, String username, String tenantId,
                       String resourceName, EventType eventType, String detail) {
        this.accessRequestId = accessRequestId;
        this.username = username;
        this.tenantId = tenantId;
        this.resourceName = resourceName;
        this.eventType = eventType;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getAccessRequestId() { return accessRequestId; }
    public String getUsername() { return username; }
    public String getTenantId() { return tenantId; }
    public String getResourceName() { return resourceName; }
    public EventType getEventType() { return eventType; }
    public String getDetail() { return detail; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    public void setId(Long id) { this.id = id; }
    public void setAccessRequestId(Long accessRequestId) { this.accessRequestId = accessRequestId; }
    public void setUsername(String username) { this.username = username; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public void setDetail(String detail) { this.detail = detail; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
