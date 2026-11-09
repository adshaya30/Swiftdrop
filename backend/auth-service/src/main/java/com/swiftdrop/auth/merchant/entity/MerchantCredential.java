package com.swiftdrop.auth.merchant.entity;

import com.swiftdrop.common.enums.CredentialStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * API credential of a merchant (SRS 6.3 merchant_credentials, FR-AUTH-018). Only the SHA-256 hash of the secret is
 * stored; the secret itself is shown once at creation. Entities are never serialised: DTOs only.
 */
@Entity
@Table(name = "merchant_credentials")
public class MerchantCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "client_id", nullable = false, unique = true, length = 40)
    private String clientId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "client_secret_hash", nullable = false, length = 64)
    private String clientSecretHash;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CredentialStatus status = CredentialStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected MerchantCredential() {
    }

    public MerchantCredential(UUID merchantId, String clientId, String clientSecretHash, String label) {
        this.merchantId = merchantId;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.label = label;
        this.status = CredentialStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getClientId() { return clientId; }
    public String getClientSecretHash() { return clientSecretHash; }
    public String getLabel() { return label; }
    public CredentialStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }

    public void markUsed(Instant when) {
        this.lastUsedAt = when;
    }

    public void revoke(Instant when) {
        this.status = CredentialStatus.REVOKED;
        this.revokedAt = when;
    }
}
