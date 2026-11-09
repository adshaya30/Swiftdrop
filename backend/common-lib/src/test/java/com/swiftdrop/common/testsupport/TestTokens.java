package com.swiftdrop.common.testsupport;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.security.JwtClaims;
import com.swiftdrop.common.security.JwtPublicKeyProvider;
import com.swiftdrop.common.security.SwiftDropJwtDecoder;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test support for every member: an RSA key pair and signed tokens for any role, typ, mid, scope, pwc, expired or
 * with a wrong audience/issuer, so authorisation tests need no auth-service.
 *
 * <pre>
 * static final TestTokens TOKENS = TestTokens.create();
 * &#64;Bean JwtPublicKeyProvider keys() { return TOKENS.publicKeyProvider(); }
 * mockMvc.perform(get("/x").header("Authorization", TOKENS.admin().bearer()))
 * </pre>
 */
public final class TestTokens {

    private final KeyPair keyPair;

    private TestTokens(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public static TestTokens create() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return new TestTokens(generator.generateKeyPair());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public RSAPublicKey publicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    public JwtPublicKeyProvider publicKeyProvider() {
        return this::publicKey;
    }

    /** The same decoder (RS256, iss, aud, skew) the services use. */
    public JwtDecoder decoder() {
        return SwiftDropJwtDecoder.create(publicKey());
    }

    /** Decodes and validates a token produced by this instance. */
    public Jwt decode(String token) {
        return decoder().decode(token);
    }

    public Builder token() {
        return new Builder(this);
    }

    public Builder customer() {
        return token().role(Role.CUSTOMER);
    }

    public Builder admin() {
        return token().role(Role.ADMIN);
    }

    public Builder courier() {
        return token().role(Role.COURIER);
    }

    public Builder merchant(UUID mid) {
        return token().role(Role.MERCHANT).mid(mid);
    }

    /** A typ=client token (role MERCHANT) for the given merchant and scopes. */
    public Builder client(UUID mid, String... scopes) {
        return token().role(Role.MERCHANT).typ(JwtClaims.TYP_CLIENT).mid(mid).scopes(scopes);
    }

    public static final class Builder {
        private final TestTokens owner;
        private TestTokens signer;
        private UUID userId = UUID.randomUUID();
        private Role role = Role.CUSTOMER;
        private String typ = JwtClaims.TYP_USER;
        private UUID mid;
        private String[] scopes = new String[0];
        private boolean pwc;
        private String issuer = JwtClaims.ISSUER;
        private String audience = JwtClaims.AUDIENCE;
        private boolean expired;

        private Builder(TestTokens owner) {
            this.owner = owner;
            this.signer = owner;
        }

        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder typ(String typ) {
            this.typ = typ;
            return this;
        }

        public Builder mid(UUID mid) {
            this.mid = mid;
            return this;
        }

        public Builder scopes(String... scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder passwordChangeRequired(boolean pwc) {
            this.pwc = pwc;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expired() {
            this.expired = true;
            return this;
        }

        /** Signs with another key pair, so the token is invalid for the owner's decoder. */
        public Builder signedBy(TestTokens other) {
            this.signer = other;
            return this;
        }

        public String build() {
            Instant now = Instant.now();
            Instant iat = expired ? now.minusSeconds(7200) : now;
            Instant exp = expired ? now.minusSeconds(3600) : now.plusSeconds(900);
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(iat))
                    .expirationTime(Date.from(exp))
                    .jwtID(UUID.randomUUID().toString())
                    .claim(JwtClaims.ROLE, role.name())
                    .claim(JwtClaims.TYP, typ)
                    .claim(JwtClaims.PWC, pwc);
            if (mid != null) {
                claims.claim(JwtClaims.MID, mid.toString());
            }
            if (scopes.length > 0) {
                claims.claim(JwtClaims.SCOPE, String.join(" ", List.of(scopes)));
            }
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
                jwt.sign(new RSASSASigner((RSAPrivateKey) signer.keyPair.getPrivate()));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }

        /** "Bearer &lt;token&gt;" for the Authorization header. */
        public String bearer() {
            return "Bearer " + build();
        }
    }
}
