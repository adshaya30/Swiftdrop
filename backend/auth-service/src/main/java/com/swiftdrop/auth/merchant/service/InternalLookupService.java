package com.swiftdrop.auth.merchant.service;

import com.swiftdrop.auth.domain.Merchant;
import com.swiftdrop.auth.domain.MerchantRepository;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserRepository;
import com.swiftdrop.auth.merchant.dto.InternalMerchantView;
import com.swiftdrop.auth.merchant.dto.InternalUserContact;
import com.swiftdrop.auth.merchant.mapper.InternalMapper;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only lookups for other services (SRS 7.4, FR-AUTH-022). Unknown or malformed ids give 404. */
@Service
public class InternalLookupService {

    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final InternalMapper mapper;

    public InternalLookupService(UserRepository userRepository, MerchantRepository merchantRepository,
                                 InternalMapper mapper) {
        this.userRepository = userRepository;
        this.merchantRepository = merchantRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public InternalUserContact contact(String id) {
        User user = userRepository.findById(parse(id)).orElseThrow(() -> notFound("User"));
        return mapper.toContact(user);
    }

    @Transactional(readOnly = true)
    public InternalMerchantView merchant(String merchantId) {
        Merchant merchant = merchantRepository.findById(parse(merchantId)).orElseThrow(() -> notFound("Merchant"));
        User user = userRepository.findById(merchant.getUserId()).orElseThrow(() -> notFound("Merchant"));
        return mapper.toMerchantView(merchant, user);
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw notFound("Resource");
        }
    }

    private static DomainException notFound(String what) {
        return new DomainException(ErrorCode.NOT_FOUND, what + " not found.");
    }
}
