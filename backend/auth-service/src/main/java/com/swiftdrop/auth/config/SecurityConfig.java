package com.swiftdrop.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.security.JwtAuthenticationFilter;
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
import java.util.Map;
import java.util.UUID;

/**
 * Security configuration for the auth-service.
 * Public endpoints: register, login, refresh, logout, OAuth, actuator health.
 * Protected endpoints: /api/v1/me/**
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-003, FR-AUTH-007, FR-AUTH-008, FR-AUTH-012, FR-AUTH-013, FR-AUTH-014
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
                // Public authentication endpoints (do not require Bearer JWT)
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout"
                ).permitAll()
                // Public OAuth & auth config endpoints (FR-AUTH-009, FR-AUTH-011)
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/auth/config",
                        "/api/v1/auth/google/login",
                        "/api/v1/auth/google/callback"
                ).permitAll()
                // Actuator health for orchestration
                .requestMatchers("/actuator/health").permitAll()
                // Protected profile & change-password endpoints
                .requestMatchers("/api/v1/me/**").authenticated()
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt with cost factor 12 as required by SRS v2.1 FR-AUTH-001.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
