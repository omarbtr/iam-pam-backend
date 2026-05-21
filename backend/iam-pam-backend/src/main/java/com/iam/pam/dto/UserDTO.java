package com.iam.pam.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class UserDTO {

    /**
     * Requête pour assigner un tenant-admin à un tenant
     */
    public static class AdminAssignRequest {
        @NotBlank(message = "Username is required")
        private String username;

        public AdminAssignRequest() {}

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    /**
     * Requête d'import d'un utilisateur depuis l'annuaire AD (via Keycloak)
     */
    public static class ImportRequest {
        @NotBlank(message = "Username is required")
        private String username;

        private List<String> roles;

        public ImportRequest() {}

        public ImportRequest(String username, List<String> roles) {
            this.username = username;
            this.roles = roles;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }

    /**
     * Utilisateur trouvé dans l'annuaire AD (via Keycloak User Federation)
     */
    public static class DirectoryUser {
        private String id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Boolean enabled;
        private String source; // "LDAP", "KEYCLOAK"

        public DirectoryUser() {}

        public DirectoryUser(String id, String username, String email,
                             String firstName, String lastName, Boolean enabled, String source) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.enabled = enabled;
            this.source = source;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    /**
     * Utilisateur membre d'un tenant
     */
    public static class TenantUser {
        private String id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Boolean enabled;
        private List<String> roles;

        public TenantUser() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }

        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
