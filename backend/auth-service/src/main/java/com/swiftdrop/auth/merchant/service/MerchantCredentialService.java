package com.swiftdrop.auth.merchant.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.merchant.dto.CredentialCreatedResponse;
import com.swiftdrop.auth.merchant.dto.CredentialSummary;
import com.swiftdrop.auth.merchant.entity.MerchantCredential;
import com.swiftdrop.auth.merchant.mapper.CredentialMapper;
import com.swiftdrop.auth.merchant.repository.MerchantAdminRepository;
import com.swiftdrop.auth.merchant.repository.MerchantCredentialRepository;
import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.CredentialStatus;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.paging.Pagination;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API credentials of the logged-in merchant. SRS v2.1 - FR-AUTH-018, FR-AUTH-023, BR-25 (another merchant's
 * credential is answered with 404). At most two ACTIVE credentials per merchant.
 */
@Service
public class MerchantCredentialService {

    private static final Logger log = LoggerFactory.getLogger(MerchantCredentialService.class);

    static final int MAX_ACTIVE_CREDENTIALS = 2;
    static final int MAX_PAGE_SIZE = 100;

    private final MerchantRepository merchantRepository;
    private final MerchantAdminRepository merchantLockRepository;
    private final MerchantCredentialRepository credentialRepository;
    private final CredentialMapper mapper;
    private final AuditEvents auditEvents;

    public MerchantCredentialService(MerchantRepository merchantRepository,
                                     MerchantAdminRepository merchantLockRepository,
                                     MerchantCredentialRepository credentialRepository, CredentialMapper mapper,
                                     AuditEvents auditEvents) {
        this.merchantRepository = merchantRepository;
        this.merchantLockRepository = merchantLockRepository;
        this.credentialRepository = credentialRepository;
        this.mapper = mapper;
        this.auditEvents = auditEvents;
    }

    /**
     * Creates a credential and returns the secret, which is never retrievable again. The merchant row is locked so
     * that two parallel requests cannot both pass the limit check.
     */
    @Transactional
    public CredentialCreatedResponse create(UUID userId, String label) {
        Merchant merchant = merchantOf(userId);
        merchantLockRepository.findByIdForUpdate(merchant.getId()).orElseThrow(MerchantCredentialService::merchantMissing);

        if (credentialRepository.countByMerchantIdAndStatus(merchant.getId(), CredentialStatus.ACTIVE)
                >= MAX_ACTIVE_CREDENTIALS) {
            throw new DomainException(ErrorCode.CREDENTIAL_LIMIT_REACHED,
                    "At most " + MAX_ACTIVE_CREDENTIALS + " active credentials are allowed. Revoke one first.");
        }

        String secret = CredentialSecrets.newClientSecret();
        MerchantCredential credential = credentialRepository.saveAndFlush(new MerchantCredential(
                merchant.getId(), CredentialSecrets.newClientId(), CredentialSecrets.sha256Hex(secret), label.trim()));

        // FR-AUTH-023: no secret, no label (free text), only ids
        auditEvents.record(userId, ActorRole.MERCHANT, "CREDENTIAL_CREATED", "CREDENTIAL",
                credential.getId().toString(), Map.of("merchantId", merchant.getId().toString()));
        log.info("API credential created: merchantId={} credentialId={}", merchant.getId(), credential.getId());
        return new CredentialCreatedResponse(credential.getId(), credential.getClientId(), secret,
                credential.getLabel(), credential.getStatus(), credential.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public Pagination<CredentialSummary> list(UUID userId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Request validation failed.",
                    List.of(new FieldError("size", "page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE)));
        }
        Merchant merchant = merchantOf(userId);
        Page<MerchantCredential> result = credentialRepository.findByMerchantId(merchant.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
        return Pagination.of(result.getContent().stream().map(mapper::toSummary).toList(), page, size,
                result.getTotalElements());
    }

    /** Revokes an own credential. Another merchant's id gives 404; revoking twice is a harmless no-op. */
    @Transactional
    public void revoke(UUID userId, UUID credentialId) {
        Merchant merchant = merchantOf(userId);
        MerchantCredential credential = credentialRepository.findByIdAndMerchantId(credentialId, merchant.getId())
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Credential not found."));
        if (!credential.isActive()) {
            return;
        }
        credential.revoke(Instant.now());
        credentialRepository.save(credential);
        auditEvents.record(userId, ActorRole.MERCHANT, "CREDENTIAL_REVOKED", "CREDENTIAL",
                credential.getId().toString(), Map.of("merchantId", merchant.getId().toString()));
        log.info("API credential revoked: merchantId={} credentialId={}", merchant.getId(), credential.getId());
    }

    private Merchant merchantOf(UUID userId) {
        return merchantRepository.findByUserId(userId).orElseThrow(MerchantCredentialService::merchantMissing);
    }

    private static DomainException merchantMissing() {
        return new DomainException(ErrorCode.NOT_FOUND, "Merchant not found.");
    }
}
