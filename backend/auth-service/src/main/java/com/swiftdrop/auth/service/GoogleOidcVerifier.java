package com.swiftdrop.auth.service;

import com.swiftdrop.auth.dto.response.GoogleUserClaims;

/**
 * Interface for validating Google OpenID Connect ID Tokens.
 * SRS v2.1 – Flow 4.5, FR-AUTH-009
 */
public interface GoogleOidcVerifier {

    /**
     * Validates the OIDC ID token structure, cryptographic signature,
     * issuer, audience, expiration, nonce, and verified email status.
     *
     * @param idTokenString raw JWT from Google token endpoint
     * @param expectedNonce nonce stored during login initiation
     * @return verified claims
     * @throws SecurityException if verification fails
     */
    GoogleUserClaims verifyIdToken(String idTokenString, String expectedNonce);
}

