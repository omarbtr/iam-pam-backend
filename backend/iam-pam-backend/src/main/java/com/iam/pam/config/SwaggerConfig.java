package com.iam.pam.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // Informations sur l'API
                .info(new Info()
                        .title("IAM & PAM Platform API")
                        .description("""
                    Backend API for Identity and Access Management (IAM)
                    and Privileged Access Management (PAM) Platform.
                    
                    Authentication: Use Keycloak JWT Bearer token.
                    Click 'Authorize' and paste your Bearer token.
                    
                    To get a token:
                    POST http://localhost:8080/realms/iam-pam-realm/protocol/openid-connect/token
                    """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("PFE Team")
                                .email("contact@iam-pam.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0"))
                )
                // Serveurs
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Development"),
                        new Server().url("https://api.iam-pam.com").description("Production")
                ))
                // Schema de securite JWT
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your Keycloak JWT token here (without 'Bearer ' prefix)")
                        )
                )
                // Appliquer la securite globalement
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}






