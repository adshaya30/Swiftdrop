package com.swiftdrop.parcel.create.service;

import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.parcel.create.client.PricingClient;
import com.swiftdrop.parcel.create.dto.*;
import com.swiftdrop.parcel.create.mapper.ParcelDetailMapper;
import com.swiftdrop.parcel.domain.*;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-PAR-004..006, AT-08/09: one shared creation path for customer and merchant callers. */
@Service
public class DefaultParcelCreationService implements ParcelCreationService {
    private final IdempotencyService idempotency;
    private final PricingClient pricing;
    private final TrackingNumberGenerator tracking;
    private final ParcelRepository parcels;
    private final OutboxService outbox;
    private final ParcelDetailMapper mapper;
    private final TransactionTemplate transaction;

    public DefaultParcelCreationService(IdempotencyService idempotency, PricingClient pricing,
            TrackingNumberGenerator tracking, ParcelRepository parcels, OutboxService outbox,
            ParcelDetailMapper mapper, PlatformTransactionManager manager) {
        this.idempotency = idempotency;
        this.pricing = pricing;
        this.tracking = tracking;
        this.parcels = parcels;
        this.outbox = outbox;
        this.mapper = mapper;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public CreationResult create(CreationRequest request) {
        // BR-21/D-24: even the merchant eligibility/discount resolver runs AFTER replay lookup.
        var replay = idempotency.findReplay(request.ownerType(), request.ownerId(),
                request.idempotencyKey(), request.requestHash());
        if (replay.isPresent()) return replay.get();
        var calculation = pricing.calculate(request.input(), request.discountPercentResolver().get());
        var fee = new CreatedParcel.Fee(calculation.currency(), calculation.baseFee(), calculation.weightFee(),
                calculation.sizeSurcharge(), calculation.discountAmount(), calculation.total());
        if (request.expectedFee() != null && calculation.total().compareTo(request.expectedFee()) != 0) {
            throw new DomainException(ErrorCode.FEE_CHANGED, "The delivery fee has changed",
                    java.util.List.of(), Map.of("fee", fee));
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return transaction.execute(tx -> persist(request, calculation));
            } catch (DataIntegrityViolationException e) {
                // The complete losing transaction is rolled back BEFORE this fresh read.
                var winner = idempotency.findReplay(request.ownerType(), request.ownerId(),
                        request.idempotencyKey(), request.requestHash());
                if (winner.isPresent()) return winner.get();
                if (constraint(e, "parcels_tracking_number_key")) continue;
                if (constraint(e, "uq_parcels_merchant_reference")) {
                    throw new DomainException(ErrorCode.DUPLICATE_MERCHANT_REFERENCE,
                            "The merchant reference is already in use");
                }
                throw e;
            }
        }
        throw new DomainException(ErrorCode.INTERNAL_ERROR, "Parcel could not be created");
    }

    private CreationResult persist(CreationRequest request, PricingClient.Calculation fee) {
        var input = request.input();
        var pickup = input.pickup();
        var dropoff = input.dropoff();
        var p = parcels.saveAndFlush(new Parcel(tracking.generate(),
                request.ownerType() == OwnerType.CUSTOMER ? request.ownerId() : null,
                request.ownerType() == OwnerType.MERCHANT ? request.ownerId() : null,
                request.merchantReference(),
                new PickupDetails(pickup.contactName(), pickup.contactPhone(), pickup.addressLine1(),
                        pickup.addressLine2(), pickup.city(), fee.originDistrict().code(),
                        fee.originDistrict().name(), pickup.postalCode()),
                new DropoffDetails(dropoff.contactName(), dropoff.contactPhone(), dropoff.addressLine1(),
                        dropoff.addressLine2(), dropoff.city(), fee.destinationDistrict().code(),
                        fee.destinationDistrict().name(), dropoff.postalCode(), dropoff.contactEmail()),
                input.parcel().weightKg(), input.parcel().size(), input.parcel().description(),
                input.deliveryInstructions(), new ZoneSnapshot(fee.originZone().code(), fee.originZone().name()),
                new ZoneSnapshot(fee.destinationZone().code(), fee.destinationZone().name()),
                fee.baseFee(), fee.weightFee(), fee.sizeSurcharge(), fee.discountAmount(), fee.total(), fee.currency()));
        var result = new CreationResult(201, request.responseBuilder().apply(mapper.toCreated(p)), false, p.getId());
        idempotency.storeSuccess(request.ownerType(), request.ownerId(), request.idempotencyKey(),
                request.requestHash(), result);
        // BR-28: construct only the shared, contact-free event record.
        outbox.add(EventTypes.PARCEL_CREATED, new ParcelCreatedData(p.getId(), p.getTrackingNumber(),
                p.getStatus(), request.ownerType(), p.getCustomerId(), p.getMerchantId(), p.getMerchantReference(),
                p.getPickup().getDistrictCode(), p.getPickup().getDistrictName(), p.getOriginZone().getName(),
                p.getDropoff().getDistrictCode(), p.getDropoff().getDistrictName(), p.getDestinationZone().getName(),
                pickup.city(), dropoff.city(), p.getCreatedAt()));
        return result;
    }

    private static boolean constraint(Throwable error, String name) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) return true;
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains(name)) return true;
        }
        return false;
    }
}
