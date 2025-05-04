package com.InsightLens.config; // Or your preferred configuration package

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // For disable()
import org.springframework.security.web.SecurityFilterChain;
// No AntPathRequestMatcher needed for global disable
// import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Development-only Spring Security configuration.
 * WARNING: This configuration permits ALL requests without authentication
 * and GLOBALLY disables CSRF protection.
 * DO NOT use this configuration in production environments.
 * Replace with a proper security configuration before deployment.
 */
@Configuration
@EnableWebSecurity
public class DevelopmentSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Permit all requests without authentication FOR DEVELOPMENT ONLY
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/**").permitAll() // Allow access to ALL paths
            )
            // Revert to globally disabling CSRF protection for development simplicity
            .csrf(AbstractHttpConfigurer::disable) // Global disable
            // Keep default form login/logout (optional if everything is permitted)
            .formLogin(withDefaults())
            .httpBasic(withDefaults());

        return http.build();
    }
}
