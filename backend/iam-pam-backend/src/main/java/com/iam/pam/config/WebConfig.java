package com.iam.pam.config;

import com.iam.pam.security.TenantInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private TenantInterceptor tenantInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                // Appliquer sur toutes les routes API proteges
                .addPathPatterns("/api/**")
                // Exclure les routes publiques et debug
                .excludePathPatterns("/api/public/**", "/api/test/**");
    }
}