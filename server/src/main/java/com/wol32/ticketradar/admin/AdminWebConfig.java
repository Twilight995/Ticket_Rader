package com.wol32.ticketradar.admin;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Protects every /api/admin/** handler with AdminAuthInterceptor. */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    static final String ADMIN_PATTERN = "/api/admin/**";

    private final AdminAuthInterceptor adminAuthInterceptor;

    public AdminWebConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns(ADMIN_PATTERN);
    }
}
