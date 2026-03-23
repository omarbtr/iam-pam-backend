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

        public Request() {}

        public Request(String name, ResourceType type, String host, Integer port, String description) {
            this.name = name;
            this.type = type;
            this.host = host;
            this.port = port;
            this.description = description;
        }

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

        public Response() {}

        public Response(Long id, String name, ResourceType type, String host, Integer port,
                        String description, String tenantId, Boolean isActive, LocalDateTime createdAt) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.host = host;
            this.port = port;
            this.description = description;
            this.tenantId = tenantId;
            this.isActive = isActive;
            this.createdAt = createdAt;
        }

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
    }
}