package com.iam.pam.security;

import com.iam.pam.entity.Tenant;
import com.iam.pam.repository.TenantRepository;
import com.iam.pam.service.KeycloakAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Intercepteur qui s'execute a chaque requete HTTP.
 *
 * Workflow :
 * JWT contient (idéalement) : groups: ["tenant-bank-a"]
 *                                    ↓
 * Si absent → fallback Keycloak Admin API (sub → groups)
 *                                    ↓
 * TenantContext.set("tenant-bank-a")
 *                                    ↓
 * Vérifie si le domaine est configuré (tenant admin uniquement)
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);

    // Endpoints accessibles même si le domaine n'est pas encore configuré
    private static final Set<String> DOMAIN_SETUP_WHITELIST = Set.of(
            "/api/admin/tenants/my/domain",
            "/api/admin/tenants/my/domain/status",
            "/api/admin/tenants/my"
    );

    private final TenantRepository tenantRepository;
    private final KeycloakAdminService keycloakAdminService;

    // @Lazy pour éviter la dépendance circulaire au démarrage
    public TenantInterceptor(TenantRepository tenantRepository,
                             @Lazy KeycloakAdminService keycloakAdminService) {
        this.tenantRepository = tenantRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {

            boolean isTenantAdmin = hasRole(authentication, "ROLE_tenant-admin")
                    && !hasRole(authentication, "ROLE_admin");

            // Chercher le tenant : d'abord dans le claim JWT "groups", sinon via API Keycloak
            List<String> jwtGroups = jwt.getClaimAsStringList("groups");
            String tenantId = null;
            String userId = jwt.getSubject();
            String username = jwt.getClaimAsString("preferred_username");

            log.info("[TenantInterceptor] user={} sub={} path={} jwtGroups={}",
                    username, userId, request.getRequestURI(), jwtGroups);

            if (jwtGroups != null && !jwtGroups.isEmpty()) {
                tenantId = jwtGroups.get(0);
                log.info("[TenantInterceptor] tenant resolved from JWT claim: {}", tenantId);
            } else if (!hasRole(authentication, "ROLE_admin")) {
                log.warn("[TenantInterceptor] no 'groups' claim in JWT for user={}, falling back to Keycloak Admin API", username);
                tenantId = keycloakAdminService.getUserFirstTenantGroup(userId);
                log.warn("[TenantInterceptor] Keycloak Admin API returned tenantId={} for user={}", tenantId, username);
            }

            // SECURITE : tout utilisateur non-admin DOIT appartenir à un groupe Keycloak (= son tenant)
            boolean isNonAdmin = !hasRole(authentication, "ROLE_admin");
            if (isNonAdmin && tenantId == null) {
                log.error("[TenantInterceptor] BLOCKED user={} (sub={}) — no tenant group found. JWT claims: {}",
                        username, userId, jwt.getClaims().keySet());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"User is not assigned to any tenant group. Contact your tenant administrator.\",\"errorCode\":\"NO_TENANT_GROUP\"}"
                );
                return false;
            }

            if (tenantId != null) {
                TenantContext.setCurrentTenant(tenantId);

                // Vérifier la configuration du domaine pour les tenant-admins
                if (isTenantAdmin) {
                    String path = request.getRequestURI();
                    boolean isWhitelisted = DOMAIN_SETUP_WHITELIST.stream()
                            .anyMatch(path::startsWith);

                    if (!isWhitelisted) {
                        Tenant tenant = tenantRepository.findByTenantId(tenantId).orElse(null);
                        if (tenant != null && !Boolean.TRUE.equals(tenant.getDomainConfigured())) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Domain not configured. Please configure your domain first.\",\"errorCode\":\"DOMAIN_NOT_CONFIGURED\",\"setupUrl\":\"/api/admin/tenants/my/domain\"}"
                            );
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // IMPORTANT : Nettoyer le ThreadLocal apres chaque requete
        TenantContext.clear();
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
