package com.iam.pam.dto;

import com.iam.pam.entity.AccessRequest.RequestStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class AccessRequestDTO {

    public static class Request {
        @NotNull(message = "Resource ID is required")
        private Long resourceId;

        private String justification;

        // null = indefinite access (no expiry)
        @Min(value = 1, message = "Duration must be at least 1 hour")
        private Integer durationHours;

        public Request() {}

        public Request(Long resourceId, String justification, Integer durationHours) {
            this.resourceId = resourceId;
            this.justification = justification;
            this.durationHours = durationHours;
        }

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

        public String getJustification() { return justification; }
        public void setJustification(String justification) { this.justification = justification; }

        public Integer getDurationHours() { return durationHours; }
        public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }
    }

    public static class ReviewRequest {
        @NotNull
        private RequestStatus status;

        private String comment;

        public ReviewRequest() {}

        public ReviewRequest(RequestStatus status, String comment) {
            this.status = status;
            this.comment = comment;
        }

        public RequestStatus getStatus() { return status; }
        public void setStatus(RequestStatus status) { this.status = status; }

        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class Response {
        private Long id;
        private String requesterUsername;
        private String tenantId;
        private Long resourceId;
        private String resourceName;
        private String resourceType;
        private String justification;
        private RequestStatus status;
        private Integer durationHours;
        private String reviewedBy;
        private String reviewComment;
        private LocalDateTime requestedAt;
        private LocalDateTime reviewedAt;
        private LocalDateTime expiresAt;
        private LocalDateTime firstAccessAt;
        private Long remainingSeconds;
        private Boolean isActive;

        public Response() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getRequesterUsername() { return requesterUsername; }
        public void setRequesterUsername(String requesterUsername) { this.requesterUsername = requesterUsername; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }

        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }

        public String getJustification() { return justification; }
        public void setJustification(String justification) { this.justification = justification; }

        public RequestStatus getStatus() { return status; }
        public void setStatus(RequestStatus status) { this.status = status; }

        public Integer getDurationHours() { return durationHours; }
        public void setDurationHours(Integer durationHours) { this.durationHours = durationHours; }

        public String getReviewedBy() { return reviewedBy; }
        public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

        public String getReviewComment() { return reviewComment; }
        public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

        public LocalDateTime getRequestedAt() { return requestedAt; }
        public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

        public LocalDateTime getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

        public LocalDateTime getFirstAccessAt() { return firstAccessAt; }
        public void setFirstAccessAt(LocalDateTime firstAccessAt) { this.firstAccessAt = firstAccessAt; }

        public Long getRemainingSeconds() { return remainingSeconds; }
        public void setRemainingSeconds(Long remainingSeconds) { this.remainingSeconds = remainingSeconds; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    }
}