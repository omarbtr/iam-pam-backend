package com.iam.pam.dto;

import com.iam.pam.entity.Resource.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class ResourceDTO {

    public static class Request {
        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Type is required")
        private ResourceType type;

        @NotBlank(message = "Host is required")
        private String host;

        private Integer port;
        private String description;

        // Credentials for bastion injection — stored encrypted, never returned in Response
        private String credentialUsername;
        private String credentialPassword;
        private String credentialPrivateKey;

        public Request() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public ResourceType getType() { return type; }
        public void setType(ResourceType type) { this.type = type; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCredentialUsername() { return credentialUsername; }
        public void setCredentialUsername(String credentialUsername) { this.credentialUsername = credentialUsername; }

        public String getCredentialPassword() { return credentialPassword; }
        public void setCredentialPassword(String credentialPassword) { this.credentialPassword = credentialPassword; }

        public String getCredentialPrivateKey() { return credentialPrivateKey; }
        public void setCredentialPrivateKey(String credentialPrivateKey) { this.credentialPrivateKey = credentialPrivateKey; }
    }

    public static class Response {
        private Long id;
        private String name;
        private ResourceType type;
        private String host;
        private Integer port;
        private String description;
        private String tenantId;
        private Boolean isActive;
        private LocalDateTime createdAt;
        private String credentialUsername;
        private Boolean hasPassword;
        private Boolean hasPrivateKey;
        // credentialPassword and credentialPrivateKey are NEVER returned

        public Response() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public ResourceType getType() { return type; }
        public void setType(ResourceType type) { this.type = type; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public Boolean getIsActive() { return isActive; }
        public void setIsActive(Boolean isActive) { this.isActive = isActive; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public String getCredentialUsername() { return credentialUsername; }
        public void setCredentialUsername(String credentialUsername) { this.credentialUsername = credentialUsername; }

        public Boolean getHasPassword() { return hasPassword; }
        public void setHasPassword(Boolean hasPassword) { this.hasPassword = hasPassword; }

        public Boolean getHasPrivateKey() { return hasPrivateKey; }
        public void setHasPrivateKey(Boolean hasPrivateKey) { this.hasPrivateKey = hasPrivateKey; }
    }
}