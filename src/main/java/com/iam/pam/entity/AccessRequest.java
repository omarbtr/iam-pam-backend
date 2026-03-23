package com.iam.pam.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Entity
@Table(name = "access_requests")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "requester_username", nullable = false)
    private String requesterUsername;

    @NotBlank
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "justification", columnDefinition = "TEXT")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "duration_hours")
    private Integer durationHours = 1;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // CONSTRUCTEURS
    public AccessRequest() {}

    public AccessRequest(Long id, String requesterUsername, String tenantId, Resource resource,
                         String justification, RequestStatus status, Integer durationHours,
                         String reviewedBy, String reviewComment, LocalDateTime requestedAt,
                         LocalDateTime reviewedAt, LocalDateTime expiresAt) {
        this.id = id;
        this.requesterUsername = requesterUsername;
        this.tenantId = tenantId;
        this.resource = resource;
        this.justification = justification;
        this.status = status;
        this.durationHours = durationHours;
        this.reviewedBy = reviewedBy;
        this.reviewComment = reviewComment;
        this.requestedAt = requestedAt;
        this.reviewedAt = reviewedAt;
        this.expiresAt = expiresAt;
    }

    // GETTERS
    public Long getId() {
        return id;
    }

    public String getRequesterUsername() {
        return requesterUsername;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Resource getResource() {
        return resource;
    }

    public String getJustification() {
        return justification;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public Integer getDurationHours() {
        return durationHours;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    // SETTERS
    public void setId(Long id) {
        this.id = id;
    }

    public void setRequesterUsername(String requesterUsername) {
        this.requesterUsername = requesterUsername;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public void setDurationHours(Integer durationHours) {
        this.durationHours = durationHours;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    // METHODE UTILITAIRE
    public boolean isActive() {
        return status == RequestStatus.APPROVED
                && expiresAt != null
                && expiresAt.isAfter(LocalDateTime.now());
    }

    // ENUM
    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED,
        EXPIRED,
        REVOKED
    }
}