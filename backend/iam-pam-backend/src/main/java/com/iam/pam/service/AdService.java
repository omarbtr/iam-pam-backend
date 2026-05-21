package com.iam.pam.service;

import com.iam.pam.dto.AdConfigDTO;
import com.iam.pam.dto.UserDTO;
import com.iam.pam.entity.Tenant;
import com.iam.pam.entity.TenantAdConfig;
import com.iam.pam.repository.TenantAdConfigRepository;
import com.iam.pam.repository.TenantRepository;
import com.iam.pam.security.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.ws.rs.core.Response;
import java.util.List;

@Service
@Transactional
public class AdService {

    private static final Logger log = LoggerFactory.getLogger(AdService.class);

    private final TenantAdConfigRepository adConfigRepository;
    private final TenantRepository tenantRepository;
    private final LdapService ldapService;
    private final KeycloakAdminService keycloakAdminService;
    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public AdService(TenantAdConfigRepository adConfigRepository,
                     TenantRepository tenantRepository,
                     LdapService ldapService,
                     @Lazy KeycloakAdminService keycloakAdminService,
                     Keycloak keycloak) {
        this.adConfigRepository = adConfigRepository;
        this.tenantRepository = tenantRepository;
        this.ldapService = ldapService;
        this.keycloakAdminService = keycloakAdminService;
        this.keycloak = keycloak;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Configuration AD
    // ──────────────────────────────────────────────────────────────────────────

    public AdConfigDTO.Response saveConfig(AdConfigDTO.SaveRequest dto) {
        String tenantId = TenantContext.getCurrentTenant();

        TenantAdConfig config = adConfigRepository.findByTenantId(tenantId)
                .orElse(null);
        boolean isNew = (config == null);
        if (isNew) {
            if (dto.getBindPassword() == null || dto.getBindPassword().isBlank()) {
                throw new IllegalArgumentException("Bind password is required for new AD configuration");
            }
            config = new TenantAdConfig();
        }

        config.setTenantId(tenantId);
        config.setServerUrl(dto.getServerUrl());
        config.setPort(dto.getPort() != null ? dto.getPort() : 389);
        config.setUseSsl(dto.getUseSsl() != null ? dto.getUseSsl() : false);
        config.setBindDn(dto.getBindDn());
        // Only update password if a new one is provided; otherwise keep existing
        if (dto.getBindPassword() != null && !dto.getBindPassword().isBlank()) {
            config.setBindPassword(dto.getBindPassword());
        }
        config.setUserSearchBase(dto.getUserSearchBase());
        if (dto.getUserSearchFilter() != null) config.setUserSearchFilter(dto.getUserSearchFilter());
        if (dto.getUsernameAttribute() != null) config.setUsernameAttribute(dto.getUsernameAttribute());
        if (dto.getEmailAttribute() != null) config.setEmailAttribute(dto.getEmailAttribute());
        if (dto.getFirstnameAttribute() != null) config.setFirstnameAttribute(dto.getFirstnameAttribute());
        if (dto.getLastnameAttribute() != null) config.setLastnameAttribute(dto.getLastnameAttribute());

        config = adConfigRepository.save(config);
        log.info("AD config saved for tenant {}", tenantId);
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public AdConfigDTO.Response getConfig() {
        String tenantId = TenantContext.getCurrentTenant();
        TenantAdConfig config = adConfigRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("AD not configured for this tenant"));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public boolean hasConfig() {
        String tenantId = TenantContext.getCurrentTenant();
        return adConfigRepository.existsByTenantId(tenantId);
    }

    public void testConnection() {
        String tenantId = TenantContext.getCurrentTenant();
        TenantAdConfig config = adConfigRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("AD not configured for this tenant"));
        ldapService.testConnection(config);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Recherche dans l'AD
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdConfigDTO.AdUser> searchUsers(String query) {
        String tenantId = TenantContext.getCurrentTenant();
        TenantAdConfig config = adConfigRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("AD not configured for this tenant. Please configure your AD connection first."));
        return ldapService.searchUsers(config, query);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Création d'un utilisateur dans l'AD (LDAP add)
    // ──────────────────────────────────────────────────────────────────────────

    public AdConfigDTO.AdUser createAdUser(AdConfigDTO.CreateAdUserRequest dto) {
        String tenantId = TenantContext.getCurrentTenant();
        TenantAdConfig config = adConfigRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("AD not configured for this tenant"));

        // Setting unicodePwd requires LDAPS
        if (!Boolean.TRUE.equals(config.getUseSsl())) {
            throw new IllegalStateException(
                    "La création d'utilisateurs AD nécessite une connexion LDAPS (SSL). " +
                    "Activez SSL dans la configuration AD du tenant.");
        }

        // Create the entry in Active Directory
        AdConfigDTO.AdUser created = ldapService.createUser(config, dto);
        log.info("AD user created in directory: {}", dto.getUsername());

        // Optionally import into Keycloak immediately
        if (dto.isImportToKeycloak()) {
            AdConfigDTO.ImportAdUserRequest importReq = new AdConfigDTO.ImportAdUserRequest();
            importReq.setUsername(dto.getUsername());
            importReq.setEmail(dto.getEmail());
            importReq.setFirstName(dto.getFirstName());
            importReq.setLastName(dto.getLastName());
            importReq.setRoles(dto.getRoles() != null ? dto.getRoles() : List.of("user"));
            importAdUser(importReq);
            log.info("AD user {} also imported into Keycloak", dto.getUsername());
        }

        return created;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Import d'un utilisateur AD dans le tenant (création dans Keycloak)
    // ──────────────────────────────────────────────────────────────────────────

    public UserDTO.TenantUser importAdUser(AdConfigDTO.ImportAdUserRequest dto) {
        String tenantId = TenantContext.getCurrentTenant();

        // Vérifier la limite d'utilisateurs
        Tenant tenant = tenantRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        if (!tenant.canAddUser()) {
            throw new IllegalStateException(
                    "Tenant has reached maximum user limit (" + tenant.getMaxUsers() + ")");
        }

        UsersResource usersResource = keycloak.realm(realm).users();

        // Vérifier si l'utilisateur existe déjà dans Keycloak
        List<UserRepresentation> existing = usersResource.search(dto.getUsername(), true);

        String userId;
        UserRepresentation userRep;

        if (!existing.isEmpty()) {
            // Utilisateur déjà dans Keycloak (peut-être d'un autre import)
            userRep = existing.get(0);
            userId = userRep.getId();
            log.info("User {} already exists in Keycloak, reusing", dto.getUsername());
        } else {
            // Créer l'utilisateur dans Keycloak avec les infos venant de l'AD
            userRep = new UserRepresentation();
            userRep.setUsername(dto.getUsername());
            userRep.setEmail(dto.getEmail());
            userRep.setFirstName(dto.getFirstName());
            userRep.setLastName(dto.getLastName());
            userRep.setEnabled(true);
            userRep.setEmailVerified(true);

            Response response = usersResource.create(userRep);
            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed to create user in Keycloak: HTTP " + response.getStatus());
            }

            // Récupérer l'ID du nouvel utilisateur
            String location = response.getHeaderString("Location");
            userId = location.substring(location.lastIndexOf('/') + 1);
            log.info("User {} created in Keycloak with id {}", dto.getUsername(), userId);
        }

        // Ajouter au groupe du tenant
        String groupId = keycloakAdminService.findGroupIdByNamePublic(tenantId);
        if (groupId == null) {
            throw new EntityNotFoundException("Tenant group not found in Keycloak: " + tenantId);
        }

        // Vérifier si déjà dans le groupe
        List<?> existingGroups = usersResource.get(userId).groups();
        boolean alreadyInGroup = existingGroups.stream()
                .anyMatch(g -> {
                    if (g instanceof org.keycloak.representations.idm.GroupRepresentation gr) {
                        return tenantId.equals(gr.getName());
                    }
                    return false;
                });

        if (!alreadyInGroup) {
            usersResource.get(userId).joinGroup(groupId);
            tenant.incrementUserCount();
            tenantRepository.save(tenant);
        }

        // Assigner les rôles
        List<String> roles = dto.getRoles() != null ? dto.getRoles() : List.of("user");
        keycloakAdminService.assignRealmRolesPublic(userId, roles);

        log.info("AD user {} imported to tenant {}", dto.getUsername(), tenantId);

        // Recharger pour avoir les infos à jour
        UserRepresentation finalUser = usersResource.get(userId).toRepresentation();
        return new UserDTO.TenantUser() {{
            setId(finalUser.getId());
            setUsername(finalUser.getUsername());
            setEmail(finalUser.getEmail());
            setFirstName(finalUser.getFirstName());
            setLastName(finalUser.getLastName());
            setEnabled(finalUser.isEnabled());
            setRoles(roles);
        }};
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapper
    // ──────────────────────────────────────────────────────────────────────────

    private AdConfigDTO.Response toResponse(TenantAdConfig config) {
        AdConfigDTO.Response r = new AdConfigDTO.Response();
        r.setId(config.getId());
        r.setServerUrl(config.getServerUrl());
        r.setPort(config.getPort());
        r.setUseSsl(config.getUseSsl());
        r.setBindDn(config.getBindDn());
        // bindPassword NOT returned for security
        r.setUserSearchBase(config.getUserSearchBase());
        r.setUserSearchFilter(config.getUserSearchFilter());
        r.setUsernameAttribute(config.getUsernameAttribute());
        r.setEmailAttribute(config.getEmailAttribute());
        r.setFirstnameAttribute(config.getFirstnameAttribute());
        r.setLastnameAttribute(config.getLastnameAttribute());
        r.setConfiguredAt(config.getConfiguredAt());
        return r;
    }
}
