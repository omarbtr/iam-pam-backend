package com.iam.pam.dto;

import com.iam.pam.entity.AuditLog.AuditAction;
import com.iam.pam.entity.AuditLog.AuditResult;
import java.time.LocalDateTime;

public class AuditLogDTO {

    public static class Response {
        private Long id;
        private AuditAction action;
        private String username;
        private String tenantId;
        private String resourceName;
        private Long accessRequestId;
        private String details;
        private String ipAddress;
        private AuditResult result;
        private LocalDateTime timestamp;

        public Response() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public AuditAction getAction() { return action; }
        public void setAction(AuditAction action) { this.action = action; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }

        public Long getAccessRequestId() { return accessRequestId; }
        public void setAccessRequestId(Long accessRequestId) { this.accessRequestId = accessRequestId; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public AuditResult getResult() { return result; }
        public void setResult(AuditResult result) { this.result = result; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}