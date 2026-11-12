package com.swiftdrop.parcel.order.service;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.error.FieldError;
import com.swiftdrop.parcel.create.dto.InternalParcelDetailDto;
import com.swiftdrop.parcel.create.mapper.ParcelDetailMapper;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.lifecycle.dto.TransitionCommand;
import com.swiftdrop.parcel.lifecycle.service.ParcelTransitionService;
import com.swiftdrop.parcel.order.dto.InternalTransitionRequest;
import com.swiftdrop.parcel.order.dto.InternalTransitionResponse;
import com.swiftdrop.parcel.order.dto.RecipientContact;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-PAR-016: the internal endpoints of SRS 7.4, used by delivery-service (Flow 3) and notification-service. */
@Service
public class InternalParcelService {

    static final Set<ActorRole> TRANSITION_ACTORS = Set.of(ActorRole.COURIER, ActorRole.ADMIN);

    private final ParcelRepository parcels;
    private final ParcelDetailMapper mapper;
    private final ParcelTransitionService transitions;

    public InternalParcelService(ParcelRepository parcels, ParcelDetailMapper mapper,
                                 ParcelTransitionService transitions) {
        this.parcels = parcels;
        this.mapper = mapper;
        this.transitions = transitions;
    }

    /** Full admin detail plus statusSequence (Flow 3 compares it after a lost transition response). */
    @Transactional(readOnly = true)
    public InternalParcelDetailDto detail(UUID id) {
        return mapper.toInternalDetail(find(id));
    }

    @Transactional(readOnly = true)
    public RecipientContact recipient(UUID id) {
        var dropoff = find(id).getDropoff();
        return new RecipientContact(dropoff.getContactName(), dropoff.getContactPhone(), dropoff.getContactEmail());
    }

    /** Business errors (404, 409 INVALID_STATUS_TRANSITION, 409 CONCURRENT_UPDATE, 400) keep their codes (SRS 7.2). */
    public InternalTransitionResponse transition(UUID id, InternalTransitionRequest request) {
        if (!TRANSITION_ACTORS.contains(request.actorRole())) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Validation failed",
                    List.of(new FieldError("actorRole", "must be COURIER or ADMIN")));
        }
        return InternalTransitionResponse.from(transitions.transition(new TransitionCommand(id, request.toStatus(),
                request.actorUserId(), request.actorRole(), request.note(), request.failureReason(),
                request.recipientName())));
    }

    private Parcel find(UUID id) {
        return parcels.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Parcel not found"));
    }
}
