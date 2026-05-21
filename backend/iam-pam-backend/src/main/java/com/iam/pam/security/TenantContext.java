package com.iam.pam.security;

/**
 * Stocke le tenant_id courant dans un ThreadLocal
 * Chaque requete HTTP a son propre thread, donc son propre tenant
 *
 * Workflow :
 * 1. Requete arrive avec JWT
 * 2. TenantInterceptor extrait tenant_id du JWT
 * 3. TenantContext.set(tenantId)
 * 4. Service utilise TenantContext.get() pour savoir quel schema utiliser
 * 5. TenantContext.clear() a la fin de la requete
 */
public class TenantContext {

    // ThreadLocal = variable locale a chaque thread (chaque requete HTTP)
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    private TenantContext() {
        // Classe utilitaire, pas d'instanciation
    }
}