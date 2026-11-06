package com.swiftdrop.auth.google.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Stores transient OAuth 2.0 PKCE code_verifier and nonce for active login attempts.
 * SRS v2.1 – Section 6.3, Flow 4.5
 */
@Entity
@Table(name = "oauth_login_states")
public class OAuthLoginState {

    @Id
    @Column(name = "state_hash", length = 64, nullable = false)
    private String stateHash;

    @Column(name = "nonce", length = 64, nullable = false)
    private String nonce;

    @Column(name = "code_verifier", length = 128, nullable = false)
    private String codeVerifier;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public OAuthLoginState() {}

    public OAuthLoginState(String stateHash, String nonce, String codeVerifier, Instant expiresAt) {
        this.stateHash = stateHash;
        this.nonce = nonce;
        this.codeVerifier = codeVerifier;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getStateHash() { return stateHash; }
    public void setStateHash(String stateHash) { this.stateHash = stateHash; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public String getCodeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
