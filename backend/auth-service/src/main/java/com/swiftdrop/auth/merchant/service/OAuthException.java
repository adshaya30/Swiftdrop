package com.swiftdrop.auth.merchant.service;

/** Error of the token endpoint in RFC 6749 section 5.2 format: error code, description and HTTP status. */
public class OAuthException extends RuntimeException {

    private final String error;
    private final int status;

    public OAuthException(String error, String description, int status) {
        super(description);
        this.error = error;
        this.status = status;
    }

    public static OAuthException invalidRequest(String description) {
        return new OAuthException("invalid_request", description, 400);
    }

    public static OAuthException unsupportedGrantType() {
        return new OAuthException("unsupported_grant_type", "Only the client_credentials grant is supported", 400);
    }

    public static OAuthException invalidScope() {
        return new OAuthException("invalid_scope", "Allowed scopes are orders:read and orders:write", 400);
    }

    /** One generic message for every cause, so that nothing reveals which part of the credentials was wrong. */
    public static OAuthException invalidClient() {
        return new OAuthException("invalid_client", "Client authentication failed", 401);
    }

    public String error() {
        return error;
    }

    public int status() {
        return status;
    }
}
