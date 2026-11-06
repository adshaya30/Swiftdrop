package com.swiftdrop.common.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Builds the decoder: RS256 only, iss=swiftdrop, aud=swiftdrop-api, exp with 30 s skew. */
public final class SwiftDropJwtDecoder {

    private SwiftDropJwtDecoder() {
    }

    public static NimbusJwtDecoder create(RSAPublicKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(JwtClaims.CLOCK_SKEW_SECONDS)),
                new JwtIssuerValidator(JwtClaims.ISSUER),
                audienceValidator()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.contains(JwtClaims.AUDIENCE)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The audience is not accepted", null));
        };
    }
}
