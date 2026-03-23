package com.iam.pam.dto;

import com.iam.pam.entity.TenantService;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class TenantServiceDTO {

    public static class Response {
        private Long id;
        private TenantService.ServiceType serviceType;
        private Boolean isActive;
        private LocalDateTime subscribedAt;
        private LocalDateTime expiresAt;
        private Boolean isExpired;

        public Response() {}

        public Response(Long id, TenantService.ServiceType serviceType, Boolean isActive,
                        LocalDateTime subscribedAt, LocalDateTime expiresAt, Boolean isExpired) {
            this.id = id;
            this.serviceType = serviceType;
            this.isActive = isActive;
            this.subscribedAt = subscribedAt;
            this.expiresAt = expiresAt;
            this.isExpired = isExpired;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public TenantService.ServiceType getServiceType() { return serviceType; }
        public void setServiceType(TenantService.ServiceType serviceType) { this.serviceType = serviceType; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

        public LocalDateTime getSubscribedAt() { return subscribedAt; }
        public void setSubscribedAt(LocalDateTime subscribedAt) { this.subscribedAt = subscribedAt; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

        public Boolean getIsExpired() { return isExpired; }
        public void setIsExpired(Boolean isExpired) { this.isExpired = isExpired; }
    }

    public static class SubscribeRequest {
        @NotNull(message = "Service type is required")
        private TenantService.ServiceType serviceType;

        private LocalDateTime expiresAt;

        public SubscribeRequest() {}

        public SubscribeRequest(TenantService.ServiceType serviceType, LocalDateTime expiresAt) {
            this.serviceType = serviceType;
            this.expiresAt = expiresAt;
        }

        public TenantService.ServiceType getServiceType() { return serviceType; }
        public void setServiceType(TenantService.ServiceType serviceType) { this.serviceType = serviceType; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }
}
