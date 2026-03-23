package com.iam.pam.service;

import com.iam.pam.dto.UserDTO;
import com.iam.pam.entity.Tenant;
import com.iam.pam.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminService.class);

    private final Keycloak keycloak;
    private final TenantRepository tenantRepository;

    @Value("${keycloak.realm}")
    private String realm;

    public KeycloakAdminService(Keycloak keycloak, TenantRepository tenantRepository) {
        this.keycloak = keycloak;
        this.tenantRepository = tenantRepository;
    }

    private RealmResource getRealmResource() {
        return keycloak.realm(realm);
    }

    // ========================================================================
    // GESTION DES GROUPES (Tenants dans Keycloak)
    // ========================================================================

    /**
     * Créer un groupe Keycloak pour un tenant
     */
    public void createTenantGroup(String tenantId) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(tenantId);

        try {
            getRealmResource().groups().add(group);
            log.info("Keycloak group created for tenant: {}", tenantId);
        } catch (Exception e) {
            log.error("Failed to create Keycloak group for tenant: {}", tenantId, e);
            throw new RuntimeException("Failed to create tenant group in Keycloak", e);
        }
    }

    // ========================================================================
    // RECHERCHE DANS L'ANNUAIRE (AD via Keycloak User Federation)
    // ========================================================================

    /**
     * Rechercher des utilisateurs dans l'annuaire (AD/LDAP fédéré dans Keycloak)
     */
    public List<UserDTO.DirectoryUser> searchDirectoryUsers(String search) {
        UsersResource usersResource = getRealmResource().users();

        List<UserRepresentation> users = usersResource.search(search, 0, 50);

        return users.stream()
                .map(this::toDirectoryUser)
                .collect(Collectors.toList());
    }

    /**
     * Lister tous les utilisateurs de l'annuaire
     */
    public List<UserDTO.DirectoryUser> listDirectoryUsers(int first, int max) {
        UsersResource usersResource = getRealmResource().users();

        List<UserRepresentation> users = usersResource.list(first, max);

        return users.stream()
                .map(this::toDirectoryUser)
                .collect(Collectors.toList());
    }

    // ========================================================================
    // IMPORT UTILISATEUR DANS UN TENANT
    // ========================================================================

    /**
     * Importer un utilisateur de l'annuaire dans un tenant (l'ajouter au groupe Keycloak du tenant)
     */
    public UserDTO.TenantUser importUserToTenant(String tenantId, String username, List<String> roles) {
        // Vérifier la limite d'utilisateurs
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));

        if (!tenant.canAddUser()) {
            throw new IllegalStateException(
                    "Tenant has reached maximum user limit (" + tenant.getMaxUsers() + ")");
        }

        UsersResource usersResource = getRealmResource().users();

        // Chercher l'utilisateur dans Keycloak (qui inclut les utilisateurs fédérés AD)
        List<UserRepresentation> foundUsers = usersResource.search(username, true);
        if (foundUsers.isEmpty()) {
            throw new EntityNotFoundException("User not found in directory: " + username);
        }

        UserRepresentation user = foundUsers.get(0);
        String userId = user.getId();

        // Trouver le groupe du tenant
        String groupId = findGroupIdByName(tenantId);
        if (groupId == null) {
            throw new EntityNotFoundException("Tenant group not found in Keycloak: " + tenantId);
        }

        // Ajouter l'utilisateur au groupe du tenant
        usersResource.get(userId).joinGroup(groupId);

        // Assigner les rôles realm si spécifiés
        if (roles != null && !roles.isEmpty()) {
            assignRealmRoles(userId, roles);
        }

        // Incrémenter le compteur d'utilisateurs du tenant
        tenant.incrementUserCount();
        tenantRepository.save(tenant);

        log.info("User {} imported to tenant {} with roles {}", username, tenantId, roles);

        return toTenantUser(user, roles);
    }

    /**
     * Retirer un utilisateur d'un tenant
     */
    public void removeUserFromTenant(String tenantId, String username) {
        UsersResource usersResource = getRealmResource().users();

        List<UserRepresentation> foundUsers = usersResource.search(username, true);
        if (foundUsers.isEmpty()) {
            throw new EntityNotFoundException("User not found: " + username);
        }

        String userId = foundUsers.get(0).getId();
        String groupId = findGroupIdByName(tenantId);

        if (groupId != null) {
            usersResource.get(userId).leaveGroup(groupId);
        }

        // Décrémenter le compteur
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found: " + tenantId));
        tenant.decrementUserCount();
        tenantRepository.save(tenant);

        log.info("User {} removed from tenant {}", username, tenantId);
    }

    // ========================================================================
    // LISTER LES UTILISATEURS D'UN TENANT
    // ========================================================================

    /**
     * Lister les utilisateurs membres d'un tenant (groupe Keycloak)
     */
    public List<UserDTO.TenantUser> getTenantUsers(String tenantId) {
        String groupId = findGroupIdByName(tenantId);
        if (groupId == null) {
            return Collections.emptyList();
        }

        GroupResource groupResource = getRealmResource().groups().group(groupId);
        List<UserRepresentation> members = groupResource.members();

        return members.stream()
                .map(user -> {
                    List<String> userRoles = getUserRealmRoles(user.getId());
                    return toTenantUser(user, userRoles);
                })
                .collect(Collectors.toList());
    }

    // ========================================================================
    // MÉTHODES PRIVÉES
    // ========================================================================

    private String findGroupIdByName(String groupName) {
        List<GroupRepresentation> groups = getRealmResource().groups().groups(groupName, 0, 1);
        if (groups.isEmpty()) {
            return null;
        }
        return groups.get(0).getId();
    }

    private void assignRealmRoles(String userId, List<String> roleNames) {
        List<RoleRepresentation> roles = roleNames.stream()
                .map(roleName -> {
                    try {
                        return getRealmResource().roles().get(roleName).toRepresentation();
                    } catch (Exception e) {
                        log.warn("Role not found in Keycloak: {}", roleName);
                        return null;
                    }
                })
                .filter(role -> role != null)
                .collect(Collectors.toList());

        if (!roles.isEmpty()) {
            getRealmResource().users().get(userId).roles().realmLevel().add(roles);
        }
    }

    private List<String> getUserRealmRoles(String userId) {
        try {
            return getRealmResource().users().get(userId).roles().realmLevel().listEffective()
                    .stream()
                    .map(RoleRepresentation::getName)
                    .filter(name -> !name.startsWith("default-roles-") && !name.equals("offline_access") && !name.equals("uma_authorization"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private UserDTO.DirectoryUser toDirectoryUser(UserRepresentation user) {
        String source = "KEYCLOAK";
        if (user.getFederationLink() != null) {
            source = "LDAP";
        }

        return new UserDTO.DirectoryUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEnabled(),
                source
        );
    }

    private UserDTO.TenantUser toTenantUser(UserRepresentation user, List<String> roles) {
        UserDTO.TenantUser tenantUser = new UserDTO.TenantUser();
        tenantUser.setId(user.getId());
        tenantUser.setUsername(user.getUsername());
        tenantUser.setEmail(user.getEmail());
        tenantUser.setFirstName(user.getFirstName());
        tenantUser.setLastName(user.getLastName());
        tenantUser.setEnabled(user.isEnabled());
        tenantUser.setRoles(roles);
        return tenantUser;
    }
}
