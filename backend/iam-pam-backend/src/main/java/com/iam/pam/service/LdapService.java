package com.iam.pam.service;

import com.iam.pam.dto.AdConfigDTO;
import com.iam.pam.entity.TenantAdConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Service
public class LdapService {

    private static final Logger log = LoggerFactory.getLogger(LdapService.class);

    /**
     * Tester la connexion à l'AD du tenant.
     * @throws RuntimeException si la connexion échoue
     */
    public void testConnection(TenantAdConfig config) {
        InitialLdapContext ctx = null;
        try {
            ctx = createContext(config);
            log.info("LDAP connection test successful for server: {}:{}", config.getServerUrl(), config.getPort());
        } catch (NamingException e) {
            log.error("LDAP connection test failed for {}:{} - {}", config.getServerUrl(), config.getPort(), e.getMessage());
            throw new RuntimeException("Connexion AD échouée : " + e.getMessage(), e);
        } finally {
            closeContext(ctx);
        }
    }

    /**
     * Rechercher des utilisateurs dans l'AD selon une requête textuelle.
     */
    public List<AdConfigDTO.AdUser> searchUsers(TenantAdConfig config, String query) {
        List<AdConfigDTO.AdUser> results = new ArrayList<>();
        InitialLdapContext ctx = null;

        try {
            ctx = createContext(config);

            // Construire le filtre LDAP
            String filter = buildSearchFilter(config, query);

            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(100);
            controls.setTimeLimit(5000);
            controls.setReturningAttributes(new String[]{
                    config.getUsernameAttribute(),
                    config.getEmailAttribute(),
                    config.getFirstnameAttribute(),
                    config.getLastnameAttribute(),
                    "distinguishedName", "dn"
            });

            NamingEnumeration<SearchResult> enumeration = ctx.search(
                    config.getUserSearchBase(), filter, controls);

            while (enumeration != null && enumeration.hasMore()) {
                SearchResult result = enumeration.next();
                Attributes attrs = result.getAttributes();

                String username  = getAttr(attrs, config.getUsernameAttribute());
                String email     = getAttr(attrs, config.getEmailAttribute());
                String firstName = getAttr(attrs, config.getFirstnameAttribute());
                String lastName  = getAttr(attrs, config.getLastnameAttribute());
                String dn        = result.getNameInNamespace();

                if (username != null && !username.isBlank()) {
                    results.add(new AdConfigDTO.AdUser(username, email, firstName, lastName, dn));
                }
            }

            log.info("LDAP search '{}' returned {} results", query, results.size());

        } catch (NamingException e) {
            log.error("LDAP search error: {}", e.getMessage());
            throw new RuntimeException("Erreur de recherche LDAP : " + e.getMessage(), e);
        } finally {
            closeContext(ctx);
        }

        return results;
    }

    /**
     * Create a new user entry in Active Directory via LDAP.
     *
     * Requires LDAPS (port 636) to set unicodePwd. Caller must ensure SSL is configured
     * on the tenant's AD config before invoking this method.
     *
     * The entry is created disabled first (userAccountControl=514) then immediately
     * enabled (userAccountControl=512) after the password is set — this is the only
     * order AD permits via LDAP.
     */
    public AdConfigDTO.AdUser createUser(TenantAdConfig config, AdConfigDTO.CreateAdUserRequest req) {
        InitialLdapContext ctx = null;
        try {
            ctx = createContext(config);

            String cn = req.getFirstName() + " " + req.getLastName();
            // Build the DN relative to the search base
            String dn = "CN=" + cn + "," + config.getUserSearchBase();

            // ── Core AD attributes ──────────────────────────────────────────
            BasicAttributes attrs = new BasicAttributes(true);

            BasicAttribute objectClass = new BasicAttribute("objectClass");
            objectClass.add("top");
            objectClass.add("person");
            objectClass.add("organizationalPerson");
            objectClass.add("user");
            attrs.put(objectClass);

            attrs.put("cn",             cn);
            attrs.put("sn",             req.getLastName());
            attrs.put("givenName",      req.getFirstName());
            attrs.put("sAMAccountName", req.getUsername());
            attrs.put("mail",           req.getEmail());
            attrs.put("displayName",    cn);

            if (req.getUpn() != null && !req.getUpn().isBlank()) {
                attrs.put("userPrincipalName", req.getUpn());
            }

            // Create disabled (512 + 2 = 514): AD requires unicodePwd before enabling
            attrs.put("userAccountControl", "514");

            ctx.createSubcontext(dn, attrs);
            log.info("AD user entry created: {}", dn);

            // ── Set password (requires LDAPS) ───────────────────────────────
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                String quotedPassword = "\"" + req.getPassword() + "\"";
                byte[] unicodePwd = quotedPassword.getBytes(StandardCharsets.UTF_16LE);

                ModificationItem[] mods = new ModificationItem[]{
                    new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute("unicodePwd", unicodePwd)),
                    new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute("userAccountControl", "512"))
                };
                ctx.modifyAttributes(dn, mods);
                log.info("AD user password set and account enabled: {}", dn);
            }

            return new AdConfigDTO.AdUser(req.getUsername(), req.getEmail(),
                    req.getFirstName(), req.getLastName(), dn);

        } catch (NamingException e) {
            log.error("LDAP createUser failed for {}: {}", req.getUsername(), e.getMessage());
            throw new RuntimeException("Erreur de création d'utilisateur AD : " + e.getMessage(), e);
        } finally {
            closeContext(ctx);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers privés
    // ──────────────────────────────────────────────────────────────────────────

    private InitialLdapContext createContext(TenantAdConfig config) throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");

        String protocol = Boolean.TRUE.equals(config.getUseSsl()) ? "ldaps" : "ldap";
        env.put(Context.PROVIDER_URL,
                protocol + "://" + config.getServerUrl() + ":" + config.getPort() + "/");

        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, config.getBindDn());
        env.put(Context.SECURITY_CREDENTIALS, config.getBindPassword());

        // Timeout de connexion : 5 secondes
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "5000");

        return new InitialLdapContext(env, null);
    }

    private void closeContext(InitialLdapContext ctx) {
        if (ctx != null) {
            try { ctx.close(); } catch (NamingException ignored) {}
        }
    }

    private String getAttr(Attributes attrs, String name) {
        if (attrs == null || name == null) return null;
        try {
            Attribute attr = attrs.get(name);
            if (attr == null || attr.get() == null) return null;
            return attr.get().toString();
        } catch (NamingException e) {
            return null;
        }
    }

    /**
     * Construit un filtre LDAP robuste pour la recherche d'utilisateurs.
     * Toujours construit programmatiquement depuis la config (évite les filtres DB corrompus).
     */
    private String buildSearchFilter(TenantAdConfig config, String query) {
        if (query == null || query.isBlank()) {
            return "(|(objectClass=inetOrgPerson)(objectClass=person)(objectClass=organizationalPerson))";
        }

        String sanitized = sanitizeLdapInput(query);
        String uid = (config.getUsernameAttribute() != null && !config.getUsernameAttribute().isBlank())
                     ? config.getUsernameAttribute() : "uid";

        // Construit directement depuis les attributs configurés — pas de template DB
        String filter = "(|(" + uid + "=*" + sanitized + "*)(cn=*" + sanitized + "*)(mail=*" + sanitized + "*))";
        log.debug("LDAP search filter: {}", filter);
        return filter;
    }

    /**
     * Sanitize LDAP input to prevent LDAP injection.
     * Escapes special LDAP characters.
     */
    private String sanitizeLdapInput(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\5c")
                .replace("*",  "\\2a")
                .replace("(",  "\\28")
                .replace(")",  "\\29")
                .replace("\0", "\\00");
    }
}
