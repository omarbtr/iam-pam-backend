package com.iam.pam.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_ad_configs", schema = "shared",
       uniqueConstraints = @UniqueConstraint(columnNames = "tenant_id"))
public class TenantAdConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private String tenantId;

    /** Adresse IP ou nom DNS du serveur LDAP/AD */
    @Column(name = "server_url", nullable = false)
    private String serverUrl;

    /** Port LDAP (389) ou LDAPS (636) */
    @Column(name = "port", nullable = false)
    private Integer port = 389;

    /** Utiliser SSL/TLS (LDAPS) */
    @Column(name = "use_ssl")
    private Boolean useSsl = false;

    /** DN de liaison (ex: cn=admin,dc=company,dc=com) */
    @Column(name = "bind_dn", nullable = false)
    private String bindDn;

    /** Mot de passe de liaison */
    @Column(name = "bind_password", nullable = false)
    private String bindPassword;

    /** Base DN pour la recherche d'utilisateurs (ex: ou=users,dc=company,dc=com) */
    @Column(name = "user_search_base", nullable = false)
    private String userSearchBase;

    /** Filtre LDAP de recherche utilisateur — {0} sera remplacé par la valeur recherchée */
    @Column(name = "user_search_filter")
    private String userSearchFilter = "(|(uid={0})(cn={0})(mail={0})(sAMAccountName={0}))";

    /** Attribut LDAP pour le nom d'utilisateur (uid pour OpenLDAP, sAMAccountName pour AD) */
    @Column(name = "username_attribute")
    private String usernameAttribute = "sAMAccountName";

    /** Attribut email */
    @Column(name = "email_attribute")
    private String emailAttribute = "mail";

    /** Attribut prénom */
    @Column(name = "firstname_attribute")
    private String firstnameAttribute = "givenName";

    /** Attribut nom */
    @Column(name = "lastname_attribute")
    private String lastnameAttribute = "sn";

    @Column(name = "configured_at")
    private LocalDateTime configuredAt = LocalDateTime.now();

    public TenantAdConfig() {}

    // Getters
    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getServerUrl() { return serverUrl; }
    public Integer getPort() { return port; }
    public Boolean getUseSsl() { return useSsl; }
    public String getBindDn() { return bindDn; }
    public String getBindPassword() { return bindPassword; }
    public String getUserSearchBase() { return userSearchBase; }
    public String getUserSearchFilter() { return userSearchFilter; }
    public String getUsernameAttribute() { return usernameAttribute; }
    public String getEmailAttribute() { return emailAttribute; }
    public String getFirstnameAttribute() { return firstnameAttribute; }
    public String getLastnameAttribute() { return lastnameAttribute; }
    public LocalDateTime getConfiguredAt() { return configuredAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public void setServerUrl(String serverUrl) { this.serverUrl = serverUrl; }
    public void setPort(Integer port) { this.port = port; }
    public void setUseSsl(Boolean useSsl) { this.useSsl = useSsl; }
    public void setBindDn(String bindDn) { this.bindDn = bindDn; }
    public void setBindPassword(String bindPassword) { this.bindPassword = bindPassword; }
    public void setUserSearchBase(String userSearchBase) { this.userSearchBase = userSearchBase; }
    public void setUserSearchFilter(String userSearchFilter) { this.userSearchFilter = userSearchFilter; }
    public void setUsernameAttribute(String usernameAttribute) { this.usernameAttribute = usernameAttribute; }
    public void setEmailAttribute(String emailAttribute) { this.emailAttribute = emailAttribute; }
    public void setFirstnameAttribute(String firstnameAttribute) { this.firstnameAttribute = firstnameAttribute; }
    public void setLastnameAttribute(String lastnameAttribute) { this.lastnameAttribute = lastnameAttribute; }
    public void setConfiguredAt(LocalDateTime configuredAt) { this.configuredAt = configuredAt; }
}
