package com.swiftdrop.parcel.lifecycle.security;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.security.OwnershipPolicy;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * FR-PAR-017: ParcelAccessPolicy.
 * Enforces ownership access rules: CUSTOMER if customer_id = sub; MERCHANT if merchant_id = mid; ADMIN always.
 * A resource owned by someone else or a non-existent parcel returns 404 NOT_FOUND (BR-25).
 * The role check (403 ACCESS_DENIED) is handled by @PreAuthorize on controllers before this policy executes.
 */
@Component
public class ParcelAccessPolicy {

    private final ParcelRepository parcelRepository;

    public ParcelAccessPolicy(ParcelRepository parcelRepository) {
        this.parcelRepository = Objects.requireNonNull(parcelRepository, "parcelRepository must not be null");
    }

    /**
     * Verifies that the parcel exists and is accessible by the current user.
     *
     * @param parcelId the parcel ID
     * @param user     the authenticated caller
     * @return the accessible {@link Parcel}
     * @throws DomainException with {@link ErrorCode#NOT_FOUND} if the parcel does not exist or does not belong to user
     */
    public Parcel requireAccessible(UUID parcelId, CurrentUser user) {
        if (parcelId == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));

        if (!OwnershipPolicy.isOwnerOrAdmin(user, parcel.getCustomerId(), parcel.getMerchantId())) {
            // BR-25: Resource owned by someone else returns 404 NOT_FOUND
            throw new DomainException(ErrorCode.NOT_FOUND, "Parcel not found");
        }

        return parcel;
    }
}

