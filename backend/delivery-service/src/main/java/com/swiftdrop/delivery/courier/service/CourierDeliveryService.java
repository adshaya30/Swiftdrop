package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.paging.Pagination;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.delivery.courier.client.ParcelClient;
import com.swiftdrop.delivery.courier.dto.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.courier.dto.DeliveryProofDto;
import com.swiftdrop.delivery.courier.dto.FailureReasonDto;
import com.swiftdrop.delivery.courier.dto.ParcelInternalDetailDto;
import com.swiftdrop.delivery.courier.dto.ParcelStatusUpdateResponse;
import com.swiftdrop.delivery.courier.dto.ParcelTransitionRequest;
import com.swiftdrop.delivery.courier.dto.ParcelTransitionResponse;
import com.swiftdrop.delivery.courier.entity.DeliveryProof;
import com.swiftdrop.delivery.courier.entity.ProofPhoto;
import com.swiftdrop.delivery.courier.repository.DeliveryProofRepository;
import com.swiftdrop.delivery.courier.repository.ProofPhotoRepository;
import com.swiftdrop.delivery.domain.CourierDeliveryQueries;
import com.swiftdrop.delivery.domain.Delivery;
import com.swiftdrop.delivery.domain.DeliveryListItem;
import com.swiftdrop.delivery.domain.DeliveryRepository;
import com.swiftdrop.delivery.domain.Scope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourierDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(CourierDeliveryService.class);

    private final DeliveryRepository deliveryRepository;
    private final CourierDeliveryQueries courierDeliveryQueries;
    private final DeliveryProofRepository deliveryProofRepository;
    private final ProofPhotoRepository proofPhotoRepository;
    private final ParcelClient parcelClient;
    private final PhotoProcessingService photoProcessingService;

    public CourierDeliveryService(
            DeliveryRepository deliveryRepository,
            CourierDeliveryQueries courierDeliveryQueries,
            DeliveryProofRepository deliveryProofRepository,
            ProofPhotoRepository proofPhotoRepository,
            ParcelClient parcelClient,
            PhotoProcessingService photoProcessingService) {
        this.deliveryRepository = deliveryRepository;
        this.courierDeliveryQueries = courierDeliveryQueries;
        this.deliveryProofRepository = deliveryProofRepository;
        this.proofPhotoRepository = proofPhotoRepository;
        this.parcelClient = parcelClient;
        this.photoProcessingService = photoProcessingService;
    }

    /**
     * FR-DEL-003: Lists deliveries assigned to courier with scope today, active, or completed.
     */
    public Pagination<DeliveryListItem> listDeliveries(UUID courierId, String scopeParam, int page, int size) {
        Scope scope = Scope.TODAY;
        if (scopeParam != null && !scopeParam.isBlank()) {
            try {
                scope = Scope.valueOf(scopeParam.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Invalid scope: " + scopeParam);
            }
        }

        int pageSize = Math.min(Math.max(1, size), 100);
        int pageNumber = Math.max(0, page);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        Page<DeliveryListItem> pageResult = courierDeliveryQueries.listForCourier(courierId, scope, pageable);
        return Pagination.of(pageResult.getContent(), pageResult.getNumber(), pageResult.getSize(), pageResult.getTotalElements());
    }

    /**
     * FR-DEL-004: Loads delivery detail for courier (without fees).
     */
    public CourierDeliveryDetailResponse getDeliveryDetail(UUID courierId, UUID parcelId) {
        Delivery delivery = deliveryRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Delivery not found"));

        if (delivery.getCourierId() == null || !delivery.getCourierId().equals(courierId)) {
            // FR-DEL-008: not assigned to caller: 404
            throw new DomainException(ErrorCode.NOT_FOUND, "Delivery not assigned to caller");
        }

        ParcelInternalDetailDto parcelDetail = parcelClient.getParcel(parcelId);
        if (parcelDetail == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Parcel details not found");
        }

        DeliveryProofDto proofDto = null;
        if (delivery.getStatus() == ParcelStatus.DELIVERED) {
            proofDto = deliveryProofRepository.findById(parcelId)
                    .map(p -> new DeliveryProofDto(p.getRecipientName(), p.getNote(), p.isHasPhoto(), p.getCapturedAt()))
                    .orElse(null);
        }

        List<ParcelStatus> allowedStatuses = allowedNextStatuses(delivery.getStatus());
        List<FailureReasonDto> failureReasons = allowedFailureReasons(delivery.getStatus());

        return new CourierDeliveryDetailResponse(
                parcelDetail.id(),
                parcelDetail.trackingNumber(),
                delivery.getStatus(),
                parcelDetail.createdAt(),
                parcelDetail.updatedAt(),
                parcelDetail.pickup(),
                parcelDetail.dropoff(),
                parcelDetail.parcel(),
                parcelDetail.deliveryInstructions(),
                parcelDetail.originZone(),
                parcelDetail.destinationZone(),
                parcelDetail.merchantReference(),
                allowedStatuses,
                failureReasons,
                proofDto
        );
    }

    /**
     * FR-DEL-005, Flow 3: Status update with pessimistic lock, proof persistence,
     * parcel-service transition call, and lost-response re-read handling.
     */
    @Transactional
    public ParcelStatusUpdateResponse updateDeliveryStatus(
            UUID courierId,
            UUID parcelId,
            ParcelStatus toStatus,
            String note,
            String recipientName,
            FailureReason failureReason,
            MultipartFile photo) {

        // 1. Take pessimistic write lock on deliveries row where courier_id = caller (else 404)
        Delivery delivery = deliveryRepository.findByParcelIdAndCourierIdForUpdate(parcelId, courierId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Delivery not found or not assigned to caller"));

        ParcelStatus currentStatus = delivery.getStatus();
        List<ParcelStatus> allowedNext = allowedNextStatuses(currentStatus);

        // 2. Validate transition legal from local status (409 INVALID_STATUS_TRANSITION)
        if (toStatus == null || !allowedNext.contains(toStatus)) {
            throw new DomainException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "This parcel cannot move from " + currentStatus + " to " + toStatus);
        }

        // Validate payload fields based on target status
        String trimmedRecipientName = recipientName != null ? recipientName.trim() : null;
        String trimmedNote = note != null ? note.trim() : null;

        if (toStatus == ParcelStatus.DELIVERED) {
            if (trimmedRecipientName == null || trimmedRecipientName.length() < 2 || trimmedRecipientName.length() > 100) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Recipient name must be between 2 and 100 characters");
            }
            if (trimmedNote != null && trimmedNote.length() > 500) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Note must not exceed 500 characters");
            }
        } else if (toStatus == ParcelStatus.FAILED) {
            if (photo != null && !photo.isEmpty()) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Photo is only allowed with DELIVERED status");
            }
            Set<FailureReason> allowedReasons = FailureReason.allowedFor(currentStatus, false);
            if (failureReason == null || !allowedReasons.contains(failureReason)) {
                throw new DomainException(
                        ErrorCode.VALIDATION_ERROR,
                        "Failure reason is not allowed for status " + currentStatus);
            }
            if (failureReason == FailureReason.OTHER) {
                if (trimmedNote == null || trimmedNote.length() < 5 || trimmedNote.length() > 500) {
                    throw new DomainException(ErrorCode.VALIDATION_ERROR, "Note must be between 5 and 500 characters when reason is OTHER");
                }
            } else if (trimmedNote != null && trimmedNote.length() > 500) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Note must not exceed 500 characters");
            }
        } else {
            // PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY
            if (photo != null && !photo.isEmpty()) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Photo is only allowed with DELIVERED status");
            }
            if (trimmedNote != null && trimmedNote.length() > 500) {
                throw new DomainException(ErrorCode.VALIDATION_ERROR, "Note must not exceed 500 characters");
            }
        }

        // 3. Process photo if provided
        byte[] processedPhoto = null;
        boolean hasPhoto = false;
        if (photo != null && !photo.isEmpty()) {
            processedPhoto = photoProcessingService.processPhoto(photo);
            hasPhoto = true;
        }

        // 4. If DELIVERED, persist proof rows in the local transaction
        if (toStatus == ParcelStatus.DELIVERED) {
            DeliveryProof proof = new DeliveryProof(
                    parcelId,
                    trimmedRecipientName,
                    trimmedNote,
                    hasPhoto,
                    Instant.now()
            );
            deliveryProofRepository.save(proof);

            if (hasPhoto && processedPhoto != null) {
                ProofPhoto proofPhoto = new ProofPhoto(parcelId, "image/jpeg", processedPhoto);
                proofPhotoRepository.save(proofPhoto);
            }
        }

        // 5. Call parcel-service POST /internal/v1/parcels/{id}/transition
        int localSequence = delivery.getStatusSequence();
        ParcelTransitionRequest transitionRequest = new ParcelTransitionRequest(
                toStatus,
                courierId,
                ActorRole.COURIER,
                trimmedNote,
                failureReason,
                trimmedRecipientName
        );

        ParcelTransitionResponse transitionResponse;
        try {
            transitionResponse = parcelClient.transition(parcelId, transitionRequest);
        } catch (Exception ex) {
            // Flow 3 step (f): call answered 409, 5xx or timed out.
            // Re-read parcel to check if the transition was nevertheless applied.
            transitionResponse = handleTransitionFailureOrTimeout(parcelId, toStatus, localSequence, ex);
        }

        // 6. Update local delivery row
        delivery.updateStatus(toStatus, transitionResponse.sequence(), transitionResponse.statusChangedAt());
        deliveryRepository.save(delivery);

        return new ParcelStatusUpdateResponse(
                parcelId,
                toStatus,
                transitionResponse.sequence(),
                transitionResponse.statusChangedAt()
        );
    }

    private ParcelTransitionResponse handleTransitionFailureOrTimeout(
            UUID parcelId, ParcelStatus toStatus, int localSequence, Exception originalException) {
        log.warn("Parcel transition call threw exception; initiating Flow 3 re-read verification");
        try {
            ParcelInternalDetailDto reRead = parcelClient.getParcel(parcelId);
            if (reRead != null && reRead.status() == toStatus && reRead.statusSequence() == localSequence + 1) {
                log.info("Re-read verified that transition to {} with sequence {} was already applied",
                        toStatus, reRead.statusSequence());
                Instant changedAt = reRead.updatedAt() != null ? reRead.updatedAt() : Instant.now();
                return new ParcelTransitionResponse(parcelId, toStatus, reRead.statusSequence(), changedAt);
            }
        } catch (Exception reReadEx) {
            log.warn("Re-read check failed: {}", reReadEx.getMessage());
        }

        // Re-read did not match or failed: roll back entire local transaction!
        if (originalException instanceof DomainException de) {
            if (de.code() == ErrorCode.INVALID_STATUS_TRANSITION || de.code() == ErrorCode.CONCURRENT_UPDATE) {
                throw de;
            }
        }
        throw new DomainException(ErrorCode.SERVICE_UNAVAILABLE, "Dependent service unavailable");
    }

    /**
     * FR-DEL-007, BR-06: Proof of delivery lookup.
     * Allowed: owner customer, owner merchant, assigned courier, admin. Others 404.
     */
    public DeliveryProofDto getProof(CurrentUser caller, UUID parcelId) {
        Delivery delivery = deliveryRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Proof not found"));

        validateProofAccess(caller, delivery);

        DeliveryProof proof = deliveryProofRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Proof not found"));

        return new DeliveryProofDto(proof.getRecipientName(), proof.getNote(), proof.isHasPhoto(), proof.getCapturedAt());
    }

    /**
     * FR-DEL-007: Proof photo JPEG bytes lookup.
     */
    public byte[] getProofPhoto(CurrentUser caller, UUID parcelId) {
        Delivery delivery = deliveryRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Proof not found"));

        validateProofAccess(caller, delivery);

        DeliveryProof proof = deliveryProofRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Proof not found"));

        if (!proof.isHasPhoto()) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Photo not found");
        }

        ProofPhoto photo = proofPhotoRepository.findById(parcelId)
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Photo not found"));

        return photo.getData();
    }

    private void validateProofAccess(CurrentUser caller, Delivery delivery) {
        if (caller == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Proof not found");
        }
        if (caller.role() == Role.ADMIN) {
            return;
        }
        if (caller.role() == Role.CUSTOMER && caller.userId() != null && caller.userId().equals(delivery.getCustomerId())) {
            return;
        }
        if (caller.role() == Role.MERCHANT && caller.mid() != null && caller.mid().equals(delivery.getMerchantId())) {
            return;
        }
        if (caller.role() == Role.COURIER && caller.userId() != null && caller.userId().equals(delivery.getCourierId())) {
            return;
        }
        // BR-25: Resource owned by someone else -> 404
        throw new DomainException(ErrorCode.NOT_FOUND, "Proof not found");
    }

    public static List<ParcelStatus> allowedNextStatuses(ParcelStatus current) {
        if (current == null) {
            return List.of();
        }
        return switch (current) {
            case CREATED -> List.of(ParcelStatus.PICKED_UP, ParcelStatus.FAILED);
            case PICKED_UP -> List.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED);
            case IN_TRANSIT -> List.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED);
            case OUT_FOR_DELIVERY -> List.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
            default -> List.of();
        };
    }

    public static List<FailureReasonDto> allowedFailureReasons(ParcelStatus current) {
        Set<FailureReason> reasons = FailureReason.allowedFor(current, false);
        return reasons.stream()
                .map(r -> new FailureReasonDto(r.name(), r.label()))
                .toList();
    }
}

