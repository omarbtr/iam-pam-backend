package com.iam.pam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public class AdConfigDTO {

    /** Requête de sauvegarde de la configuration AD */
    public static class SaveRequest {
        @NotBlank(message = "Server URL is required")
        private String serverUrl;

        @NotNull @Min(1) @Max(65535)
        private Integer port = 389;

        private Boolean useSsl = false;

        @NotBlank(message = "Bind DN is required")
        private String bindDn;

        // Password is optional on update — backend keeps existing value if blank
        private String bindPassword;

        @NotBlank(message = "User search base is required")
        private String userSearchBase;

        private String userSearchFilter = "(|(uid={0})(cn={0})(mail={0})(sAMAccountName={0}))";
        private String usernameAttribute = "sAMAccountName";
        private String emailAttribute = "mail";
        private String firstnameAttribute = "givenName";
        private String lastnameAttribute = "sn";

        public SaveRequest() {}

        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public Boolean getUseSsl() { return useSsl; }
        public void setUseSsl(Boolean useSsl) { this.useSsl = useSsl; }
        public String getBindDn() { return bindDn; }
        public void setBindDn(String bindDn) { this.bindDn = bindDn; }
        public String getBindPassword() { return bindPassword; }
        public void setBindPassword(String bindPassword) { this.bindPassword = bindPassword; }
        public String getUserSearchBase() { return userSearchBase; }
        public void setUserSearchBase(String userSearchBase) { this.userSearchBase = userSearchBase; }
        public String getUserSearchFilter() { return userSearchFilter; }
        public void setUserSearchFilter(String userSearchFilter) { this.userSearchFilter = userSearchFilter; }
        public String getUsernameAttribute() { return usernameAttribute; }
        public void setUsernameAttribute(String usernameAttribute) { this.usernameAttribute = usernameAttribute; }
        public String getEmailAttribute() { return emailAttribute; }
        public void setEmailAttribute(String emailAttribute) { this.emailAttribute = emailAttribute; }
        public String getFirstnameAttribute() { return firstnameAttribute; }
        public void setFirstnameAttribute(String firstnameAttribute) { this.firstnameAttribute = firstnameAttribute; }
        public String getLastnameAttribute() { return lastnameAttribute; }
        public void setLastnameAttribute(String lastnameAttribute) { this.lastnameAttribute = lastnameAttribute; }
    }

    /** Réponse (mot de passe masqué) */
    public static class Response {
        private Long id;
        private String serverUrl;
        private Integer port;
        private Boolean useSsl;
        private String bindDn;
        private String userSearchBase;
        private String userSearchFilter;
        private String usernameAttribute;
        private String emailAttribute;
        private String firstnameAttribute;
        private String lastnameAttribute;
        private LocalDateTime configuredAt;

        public Response() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getServerUrl() { return serverUrl; }
        public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public Boolean getUseSsl() { return useSsl; }
        public void setUseSsl(Boolean useSsl) { this.useSsl = useSsl; }
        public String getBindDn() { return bindDn; }
        public void setBindDn(String bindDn) { this.bindDn = bindDn; }
        public String getUserSearchBase() { return userSearchBase; }
        public void setUserSearchBase(String userSearchBase) { this.userSearchBase = userSearchBase; }
        public String getUserSearchFilter() { return userSearchFilter; }
        public void setUserSearchFilter(String userSearchFilter) { this.userSearchFilter = userSearchFilter; }
        public String getUsernameAttribute() { return usernameAttribute; }
        public void setUsernameAttribute(String usernameAttribute) { this.usernameAttribute = usernameAttribute; }
        public String getEmailAttribute() { return emailAttribute; }
        public void setEmailAttribute(String emailAttribute) { this.emailAttribute = emailAttribute; }
        public String getFirstnameAttribute() { return firstnameAttribute; }
        public void setFirstnameAttribute(String firstnameAttribute) { this.firstnameAttribute = firstnameAttribute; }
        public String getLastnameAttribute() { return lastnameAttribute; }
        public void setLastnameAttribute(String lastnameAttribute) { this.lastnameAttribute = lastnameAttribute; }
        public LocalDateTime getConfiguredAt() { return configuredAt; }
        public void setConfiguredAt(LocalDateTime configuredAt) { this.configuredAt = configuredAt; }
    }

    /** Utilisateur trouvé dans l'AD du tenant */
    public static class AdUser {
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String distinguishedName;

        public AdUser() {}
        public AdUser(String username, String email, String firstName, String lastName, String dn) {
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
            this.distinguishedName = dn;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getDistinguishedName() { return distinguishedName; }
        public void setDistinguishedName(String distinguishedName) { this.distinguishedName = distinguishedName; }
    }

    /** Requête de création d'un utilisateur directement dans l'Active Directory */
    public static class CreateAdUserRequest {
        @NotBlank(message = "Username (sAMAccountName) is required")
        private String username;

        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @NotBlank(message = "Email is required")
        private String email;

        /** userPrincipalName, e.g. username@domain.com — optional, defaults to username@domain */
        private String upn;

        @NotBlank(message = "Password is required")
        private String password;

        /** If true, also import the created user into Keycloak and assign the given roles. */
        private boolean importToKeycloak = true;
        private List<String> roles;

        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { this.email = v; }
        public String getUpn() { return upn; }
        public void setUpn(String v) { this.upn = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { this.password = v; }
        public boolean isImportToKeycloak() { return importToKeycloak; }
        public void setImportToKeycloak(boolean v) { this.importToKeycloak = v; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> v) { this.roles = v; }
    }

    /** Requête d'import d'un utilisateur AD dans le tenant */
    public static class ImportAdUserRequest {
        @NotBlank
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private List<String> roles;

        public ImportAdUserRequest() {}

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public List<String> getRoles() { return roles; }
        public void setRoles(List<String> roles) { this.roles = roles; }
    }
}
