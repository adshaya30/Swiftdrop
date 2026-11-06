package com.swiftdrop.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.security.JwtAuthenticationFilter;
import com.swiftdrop.auth.security.SecurityRules;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Security configuration for auth-service adhering to Master Prompt Section 8.1.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Master Prompt Section 8.1 SecurityRules bean.
     */
    @Bean
    public SecurityRules securityRules() {
        return new SecurityRules() {
            @Override
            public List<String> publicPaths() {
                return List.of(
                        "POST /api/v1/auth/register",
                        "POST /api/v1/auth/register-merchant",
                        "POST /api/v1/auth/login",
                        "POST /api/v1/auth/refresh",
                        "POST /api/v1/auth/logout",
                        "GET /api/v1/auth/google/login",
                        "GET /api/v1/auth/google/callback",
                        "GET /api/v1/auth/config",
                        "POST /api/v1/oauth/token"
                );
            }

            @Override
            public List<String> passwordChangeWhitelist() {
                return List.of(
                        "GET /api/v1/me",
                        "POST /api/v1/me/change-password",
                        "POST /api/v1/auth/refresh",
                        "POST /api/v1/auth/logout"
                );
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    Map<String, Object> problem = new LinkedHashMap<>();
                    problem.put("type", "https://swiftdrop.com/errors/unauthorized");
                    problem.put("title", "Unauthorized");
                    problem.put("status", 401);
                    problem.put("detail", "Authentication required to access this resource");
                    problem.put("instance", request.getRequestURI());
                    problem.put("code", "UNAUTHORIZED");
                    problem.put("traceId", UUID.randomUUID().toString());
                    problem.put("timestamp", Instant.now().toString());
                    objectMapper.writeValue(response.getOutputStream(), problem);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    Map<String, Object> problem = new LinkedHashMap<>();
                    problem.put("type", "https://swiftdrop.com/errors/forbidden");
                    problem.put("title", "Forbidden");
                    problem.put("status", 403);
                    problem.put("detail", accessDeniedException.getMessage());
                    problem.put("instance", request.getRequestURI());
                    problem.put("code", "FORBIDDEN");
                    problem.put("traceId", UUID.randomUUID().toString());
                    problem.put("timestamp", Instant.now().toString());
                    objectMapper.writeValue(response.getOutputStream(), problem);
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public authentication endpoints
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/register-merchant",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/oauth/token"
                ).permitAll()
                // Public OAuth & auth config endpoints
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/auth/config",
                        "/api/v1/auth/google/login",
                        "/api/v1/auth/google/callback"
                ).permitAll()
                // Actuator health & readiness
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                // Protected profile endpoints
                .requestMatchers("/api/v1/me/**").authenticated()
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
