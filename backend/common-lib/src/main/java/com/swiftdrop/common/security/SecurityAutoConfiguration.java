package com.swiftdrop.common.security;

import com.swiftdrop.common.db.DatabaseStartupConfiguration;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.web.CommonWebConfiguration;
import com.swiftdrop.common.web.ProblemWriter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Adding the common-lib dependency plus a SecurityRules bean gives a service JWT verification, deny-by-default
 * authorisation, the internal key chain, error handling, request ids, JSON logging and database start-up retry.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
@Import({CommonWebConfiguration.class, DatabaseStartupConfiguration.class})
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtPublicKeyProvider jwtPublicKeyProvider(Environment env) {
        return new FileJwtPublicKeyProvider(env.getProperty("JWT_PUBLIC_KEY_PATH"));
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JwtPublicKeyProvider provider) {
        return SwiftDropJwtDecoder.create(provider.publicKey());
    }

    /** Fails the start-up when INTERNAL_API_KEY is missing, shorter than 32 characters or a placeholder. */
    @Bean
    @ConditionalOnMissingBean
    public InternalApiKey internalApiKey(Environment env) {
        return new InternalApiKey(env.getProperty("INTERNAL_API_KEY"));
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain swiftDropInternalFilterChain(HttpSecurity http, InternalApiKey key,
                                                            ProblemWriter writer) throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint(writer)).accessDeniedHandler(deniedHandler(writer)))
                .addFilterBefore(new InternalKeyFilter(key, writer), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 10)
    public SecurityFilterChain swiftDropApiFilterChain(HttpSecurity http, SecurityRules rules, JwtDecoder decoder,
                                                       ProblemWriter writer) throws Exception {
        AuthenticationEntryPoint entry = entryPoint(writer);
        AccessDeniedHandler denied = deniedHandler(writer);
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(r -> RulePatterns.matches(publicRules(rules), r)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(jwtConverter()))
                        .authenticationEntryPoint(entry)
                        .accessDeniedHandler(denied))
                .addFilterAfter(new ClientTokenFilter(writer), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new PasswordChangeRequiredFilter(rules::passwordChangeWhitelist, writer),
                        ClientTokenFilter.class);
        return http.build();
    }

    private static List<String> publicRules(SecurityRules rules) {
        List<String> all = new ArrayList<>(rules.publicPaths());
        all.add("GET /actuator/health");
        all.add("GET /actuator/health/**");
        return all;
    }

    /** role becomes ROLE_&lt;role&gt;, each scope becomes SCOPE_&lt;scope&gt;. */
    static JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityAutoConfiguration::authorities);
        return converter;
    }

    private static List<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> out = new ArrayList<>();
        String role = jwt.getClaimAsString(JwtClaims.ROLE);
        if (role != null) {
            out.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        CurrentUsers.scopes(jwt.getClaim(JwtClaims.SCOPE))
                .forEach(s -> out.add(new SimpleGrantedAuthority("SCOPE_" + s)));
        return out;
    }

    private static AuthenticationEntryPoint entryPoint(ProblemWriter writer) {
        return (request, response, ex) -> {
            boolean expired = false;
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t.getMessage() != null && t.getMessage().toLowerCase().contains("expired")) {
                    expired = true;
                    break;
                }
            }
            if (expired) {
                writer.write(response, ErrorCode.TOKEN_EXPIRED, "Token expired");
            } else {
                writer.write(response, ErrorCode.UNAUTHENTICATED, "Authentication required");
            }
        };
    }

    private static AccessDeniedHandler deniedHandler(ProblemWriter writer) {
        return (request, response, ex) -> writer.write(response, ErrorCode.ACCESS_DENIED, "Access denied");
    }
}
