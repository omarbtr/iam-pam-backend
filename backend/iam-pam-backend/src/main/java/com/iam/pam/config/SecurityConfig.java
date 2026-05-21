package com.iam.pam.config;

import com.nimbusds.jwt.JWTParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${face.jwt.secret}")
    private String faceJwtSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        // Allow the proxy iframe to be embedded — Spring Security adds X-Frame-Options:DENY by default
                        .frameOptions(frame -> frame.disable())
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/public/**",
                                "/ws/**",           // WebSocket upgrade — browser can't send Authorization headers
                                "/proxy/web/**",    // Web proxy — authenticated by session UUID in URL (iframe can't send JWT)
                                "/actuator/health",
                                "/actuator/info",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll()
                        // admin (platform) : gestion des tenants
                        .requestMatchers("/api/admin/tenants/my/**").hasRole("tenant-admin")
                        .requestMatchers("/api/admin/tenants/**").hasAnyRole("admin", "tenant-admin")
                        // tenant-admin : gestion users + services
                        .requestMatchers("/api/admin/users/**").hasRole("tenant-admin")
                        .requestMatchers("/api/tenant/services/**").hasAnyRole("tenant-admin", "user")
                        // Autres patterns
                        .requestMatchers("/api/admin/**").hasAnyRole("admin", "tenant-admin")
                        .requestMatchers("/api/user/face/**").authenticated()
                        .requestMatchers("/api/user/mfa/**").authenticated()
                        .requestMatchers("/api/user/**").hasAnyRole("user", "tenant-admin")
                        .requestMatchers("/api/auditor/**").hasAnyRole("auditor", "tenant-admin")
                        // Les règles fines (user, pam-access, tenant-admin) sont gérées
                        // par @PreAuthorize sur chaque méthode de contrôleur
                        .requestMatchers("/api/pam/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder keycloakDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        byte[] keyBytes = faceJwtSecret.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        System.arraycopy(keyBytes, 0, key, 0, Math.min(keyBytes.length, 32));
        SecretKeySpec secretKey = new SecretKeySpec(key, "HmacSHA256");
        NimbusJwtDecoder faceDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();

        // Keep 'iss' as a plain String — Spring's default converter tries to parse it
        // as a java.net.URL which fails when Keycloak uses a non-URL issuer string.
        MappedJwtClaimSetConverter passThrough = MappedJwtClaimSetConverter
                .withDefaults(Collections.singletonMap("iss", claim -> claim));
        keycloakDecoder.setClaimSetConverter(passThrough);
        faceDecoder.setClaimSetConverter(passThrough);

        return token -> {
            try {
                String iss = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
                if ("face-auth".equals(iss)) {
                    return faceDecoder.decode(token);
                }
            } catch (Exception ignored) {}
            return keycloakDecoder.decode(token);
        };
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Face JWT: flat "roles" claim
        if ("face-auth".equals(jwt.getClaimAsString("iss"))) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return Collections.emptyList();
            return roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());
        }

        // Keycloak JWT: realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Total-Count"
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
