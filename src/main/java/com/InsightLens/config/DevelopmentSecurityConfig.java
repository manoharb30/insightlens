package com.InsightLens.config; // Or your preferred configuration package

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // For disable()
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

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
            .cors(withDefaults()) // Enable CORS
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/graphql", "/graphiql", "/graphql/**").permitAll()
                .requestMatchers("/**").permitAll()
            )
            // Revert to globally disabling CSRF protection for development simplicity
            .csrf(AbstractHttpConfigurer::disable) // Global disable
            // Keep default form login/logout (optional if everything is permitted)
            .formLogin(withDefaults())
            .httpBasic(withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:5173")); // Your frontend URL
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
