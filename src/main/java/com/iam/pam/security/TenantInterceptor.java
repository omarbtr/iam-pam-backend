package com.iam.pam.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * Intercepteur qui s'execute a chaque requete HTTP
 *
 * Workflow :
 * JWT contient : groups: ["tenant-bank-a"]
 *                         ↓
 * Intercepteur extrait : "tenant-bank-a"
 *                         ↓
 * TenantContext.set("tenant-bank-a")
 *                         ↓
 * Service utilise le bon schema PostgreSQL : tenant_bank_a
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // Recuperer l'authentification depuis Spring Security
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            // Extraire les groups (tenants) du JWT
            List<String> groups = jwt.getClaimAsStringList("groups");

            if (groups != null && !groups.isEmpty()) {
                // Prendre le premier group comme tenant_id
                String tenantId = groups.get(0);
                TenantContext.setCurrentTenant(tenantId);
            }
        }

        return true; // Continuer le traitement de la requete
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // IMPORTANT : Nettoyer le ThreadLocal apres chaque requete
        // Sinon risque de fuite memoire et de mauvais tenant pour la prochaine requete
        TenantContext.clear();
    }
}