package com.swiftdrop.parcel.lifecycle.service;

import com.swiftdrop.common.enums.ActorRole;
import com.swiftdrop.common.enums.FailureReason;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.enums.Role;
import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import com.swiftdrop.common.events.AuditEvents;
import com.swiftdrop.common.events.EventTypes;
import com.swiftdrop.common.events.OutboxService;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelRepository;
import com.swiftdrop.parcel.lifecycle.dto.TransitionCommand;
import com.swiftdrop.parcel.lifecycle.dto.TransitionResult;
import com.swiftdrop.parcel.lifecycle.security.ParcelAccessPolicy;
import com.swiftdrop.parcel.support.ParcelFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelTransitionServiceTests {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelAccessPolicy parcelAccessPolicy;

    @Mock
    private OutboxService outboxService;

    @Mock
    private AuditEvents auditEvents;

    private final Instant fixedNow = Instant.parse("2026-11-11T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);

    private ParcelTransitionService service;

    private final UUID parcelId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID actorUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ParcelTransitionService(
                parcelRepository,
                parcelAccessPolicy,
                outboxService,
                auditEvents,
                fixedClock);
    }

    private Parcel createParcel(ParcelStatus status) {
        Parcel parcel = ParcelFixtures.parcel("LP000000001LK", customerId, null, null);
        ReflectionTestUtils.setField(parcel, "id", parcelId);
        parcel.setStatus(status);
        parcel.setStatusSequence(1);
        return parcel;
    }

    @Nested
    @DisplayName("TST-011: Allowed transitions and side-effects")
    class AllowedTransitions {

        static Stream<Arguments> allowedTransitions() {
            return Stream.of(
                    // Courier lifecycle progress
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ActorRole.COURIER, null, null, null),
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ActorRole.COURIER, null, null, null),
                    Arguments.of(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ActorRole.COURIER, null, null, null),
                    Arguments.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, ActorRole.COURIER, "John Doe", null, null),

                    // Courier fail at stages
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.FAILED, ActorRole.COURIER, null, FailureReason.SENDER_UNAVAILABLE_AT_PICKUP, null),
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.FAILED, ActorRole.COURIER, null, FailureReason.PARCEL_DAMAGED, null),
                    Arguments.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED, ActorRole.COURIER, null, FailureReason.PARCEL_DAMAGED, null),
                    Arguments.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, ActorRole.COURIER, null, FailureReason.RECIPIENT_UNAVAILABLE, null),
                    Arguments.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, ActorRole.COURIER, null, FailureReason.OTHER, "Weather condition blocked delivery"),

                    // Cancellations
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.CANCELLED, ActorRole.CUSTOMER, null, null, "Changed my mind"),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.CANCELLED, ActorRole.MERCHANT, null, null, "Out of stock"),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.CANCELLED, ActorRole.ADMIN, null, null, "Administrative cancel valid reason"),

                    // Admin overrides
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.FAILED, ActorRole.ADMIN, null, FailureReason.PARCEL_DAMAGED, "Damaged in courier transit override"),
                    Arguments.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED, ActorRole.ADMIN, null, FailureReason.PARCEL_DAMAGED, "Transit vehicle accident override"),
                    Arguments.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED, ActorRole.ADMIN, null, FailureReason.OTHER, "Severe damage during delivery override")
            );
        }

        @ParameterizedTest(name = "TST-011: {0} -> {1} by {2}")
        @MethodSource("allowedTransitions")
        @Tag("TST-011")
        void allowedTransitionSucceeds(
                ParcelStatus from,
                ParcelStatus to,
                ActorRole role,
                String recipientName,
                FailureReason failureReason,
                String note) {
            Parcel parcel = createParcel(from);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelRepository.saveAndFlush(any(Parcel.class))).thenReturn(parcel);

            TransitionCommand command = new TransitionCommand(
                    parcelId, to, actorUserId, role, note, failureReason, recipientName);

            TransitionResult result = service.transition(command);

            assertThat(result.parcelId()).isEqualTo(parcelId);
            assertThat(result.trackingNumber()).isEqualTo("LP000000001LK");
            assertThat(result.status()).isEqualTo(to);
            assertThat(result.sequence()).isEqualTo(2);
            assertThat(result.statusChangedAt()).isEqualTo(fixedNow);

            assertThat(parcel.getStatus()).isEqualTo(to);
            assertThat(parcel.getStatusSequence()).isEqualTo(2);

            boolean isTerminal = (to == ParcelStatus.DELIVERED || to == ParcelStatus.FAILED || to == ParcelStatus.CANCELLED);
            if (isTerminal) {
                assertThat(parcel.getTerminalAt()).isEqualTo(fixedNow);
            } else {
                assertThat(parcel.getTerminalAt()).isNull();
            }

            ArgumentCaptor<ParcelStatusChangedData> captor = ArgumentCaptor.forClass(ParcelStatusChangedData.class);
            verify(outboxService).add(eq(EventTypes.PARCEL_STATUS_CHANGED), captor.capture());

            ParcelStatusChangedData event = captor.getValue();
            assertThat(event.parcelId()).isEqualTo(parcelId);
            assertThat(event.trackingNumber()).isEqualTo("LP000000001LK");
            assertThat(event.sequence()).isEqualTo(2);
            assertThat(event.previousStatus()).isEqualTo(from);
            assertThat(event.status()).isEqualTo(to);
            assertThat(event.statusChangedAt()).isEqualTo(fixedNow);
            assertThat(event.actorUserId()).isEqualTo(actorUserId);
            assertThat(event.actorRole()).isEqualTo(role);
            assertThat(event.note()).isEqualTo(note);
            assertThat(event.failureReasonCode()).isEqualTo(failureReason);
            assertThat(event.ownerType()).isEqualTo(OwnerType.CUSTOMER);
            assertThat(event.customerId()).isEqualTo(customerId);
        }
    }

    @Nested
    @DisplayName("TST-011: Disallowed transitions return 409 INVALID_STATUS_TRANSITION")
    class DisallowedTransitions {

        static Stream<Arguments> disallowedTransitions() {
            return Stream.of(
                    // Skipping steps
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.IN_TRANSIT, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.OUT_FOR_DELIVERY, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.DELIVERED, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.DELIVERED, ActorRole.COURIER),

                    // Reversing steps
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.CREATED, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.IN_TRANSIT, ParcelStatus.PICKED_UP, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.IN_TRANSIT, ActorRole.COURIER),

                    // Same status
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.CREATED, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.PICKED_UP, ActorRole.COURIER),

                    // Disallowed roles
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ActorRole.CUSTOMER),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ActorRole.ADMIN),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.CANCELLED, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED, ActorRole.CUSTOMER),
                    Arguments.of(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED, ActorRole.ADMIN),
                    Arguments.of(ParcelStatus.CREATED, ParcelStatus.FAILED, ActorRole.ADMIN), // Admin cannot fail CREATED

                    // Terminal states cannot transition to anything
                    Arguments.of(ParcelStatus.DELIVERED, ParcelStatus.PICKED_UP, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED, ActorRole.ADMIN),
                    Arguments.of(ParcelStatus.FAILED, ParcelStatus.CREATED, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.FAILED, ParcelStatus.DELIVERED, ActorRole.COURIER),
                    Arguments.of(ParcelStatus.CANCELLED, ParcelStatus.CREATED, ActorRole.CUSTOMER),
                    Arguments.of(ParcelStatus.CANCELLED, ParcelStatus.PICKED_UP, ActorRole.COURIER)
            );
        }

        @ParameterizedTest(name = "TST-011: Disallowed {0} -> {1} by {2}")
        @MethodSource("disallowedTransitions")
        @Tag("TST-011")
        void disallowedTransitionsRejected(ParcelStatus from, ParcelStatus to, ActorRole role) {
            Parcel parcel = createParcel(from);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            TransitionCommand command = new TransitionCommand(
                    parcelId, to, actorUserId, role, null, null, null);

            assertThatThrownBy(() -> service.transition(command))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> {
                        DomainException de = (DomainException) ex;
                        assertThat(de.code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                        assertThat(de.detail()).isEqualTo("This parcel cannot move from " + from + " to " + to + ".");
                    });

            verify(parcelRepository, never()).saveAndFlush(any());
            verify(outboxService, never()).add(any(), any());
        }
    }

    @Nested
    @DisplayName("Input validations return 400 VALIDATION_ERROR")
    class InputValidations {

        @Test
        @Tag("TST-011")
        @DisplayName("BR-17: DELIVERED requires recipientName 2-100 characters")
        void deliveredRequiresRecipientName() {
            Parcel parcel = createParcel(ParcelStatus.OUT_FOR_DELIVERY);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            // null recipientName
            TransitionCommand cmd1 = new TransitionCommand(
                    parcelId, ParcelStatus.DELIVERED, actorUserId, ActorRole.COURIER, null, null, null);
            assertThatThrownBy(() -> service.transition(cmd1))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> {
                        DomainException de = (DomainException) ex;
                        assertThat(de.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(de.errors()).anyMatch(e -> e.field().equals("recipientName"));
                    });

            // short recipientName (< 2)
            TransitionCommand cmd2 = new TransitionCommand(
                    parcelId, ParcelStatus.DELIVERED, actorUserId, ActorRole.COURIER, null, null, "A");
            assertThatThrownBy(() -> service.transition(cmd2))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("recipientName")));

            // long recipientName (> 100)
            TransitionCommand cmd3 = new TransitionCommand(
                    parcelId, ParcelStatus.DELIVERED, actorUserId, ActorRole.COURIER, null, null, "A".repeat(101));
            assertThatThrownBy(() -> service.transition(cmd3))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("recipientName")));
        }

        @Test
        @Tag("TST-011")
        @DisplayName("BR-16: FAILED requires failureReason")
        void failedRequiresReason() {
            Parcel parcel = createParcel(ParcelStatus.PICKED_UP);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            TransitionCommand cmd = new TransitionCommand(
                    parcelId, ParcelStatus.FAILED, actorUserId, ActorRole.COURIER, null, null, null);

            assertThatThrownBy(() -> service.transition(cmd))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> {
                        DomainException de = (DomainException) ex;
                        assertThat(de.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(de.errors()).anyMatch(e -> e.field().equals("failureReason"));
                    });
        }

        @Test
        @Tag("TST-011")
        @DisplayName("BR-16 Table 2.4: FAILED reason must be allowed for stage")
        void failedReasonStageDisallowed() {
            Parcel parcel = createParcel(ParcelStatus.CREATED);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            // RECIPIENT_UNAVAILABLE not allowed at CREATED stage
            TransitionCommand cmd = new TransitionCommand(
                    parcelId, ParcelStatus.FAILED, actorUserId, ActorRole.COURIER, null, FailureReason.RECIPIENT_UNAVAILABLE, null);

            assertThatThrownBy(() -> service.transition(cmd))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> {
                        DomainException de = (DomainException) ex;
                        assertThat(de.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                        assertThat(de.errors()).anyMatch(e -> e.field().equals("failureReason"));
                    });
        }

        @Test
        @Tag("TST-011")
        @DisplayName("BR-16: FAILED with OTHER requires note 5-500 characters")
        void failedOtherRequiresNote() {
            Parcel parcel = createParcel(ParcelStatus.OUT_FOR_DELIVERY);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            // missing note
            TransitionCommand cmd1 = new TransitionCommand(
                    parcelId, ParcelStatus.FAILED, actorUserId, ActorRole.COURIER, null, FailureReason.OTHER, null);
            assertThatThrownBy(() -> service.transition(cmd1))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("note")));

            // note too short (< 5)
            TransitionCommand cmd2 = new TransitionCommand(
                    parcelId, ParcelStatus.FAILED, actorUserId, ActorRole.COURIER, "Tiny", FailureReason.OTHER, null);
            assertThatThrownBy(() -> service.transition(cmd2))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("note")));
        }

        @Test
        @Tag("TST-011")
        @DisplayName("Note length exceeds 500 characters returns 400 VALIDATION_ERROR")
        void noteExceeds500() {
            Parcel parcel = createParcel(ParcelStatus.PICKED_UP);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            TransitionCommand cmd = new TransitionCommand(
                    parcelId, ParcelStatus.IN_TRANSIT, actorUserId, ActorRole.COURIER, "A".repeat(501), null, null);

            assertThatThrownBy(() -> service.transition(cmd))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("note")));
        }
    }

    @Nested
    @DisplayName("Cancellations and Admin actions")
    class CancellationsAndAdminActions {

        @Test
        @Tag("TST-011")
        @DisplayName("BR-07: cancelAsOwner uses access policy and succeeds while CREATED")
        void cancelAsOwnerSucceeds() {
            Parcel parcel = createParcel(ParcelStatus.CREATED);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelRepository.saveAndFlush(any(Parcel.class))).thenReturn(parcel);

            CurrentUser customerUser = new CurrentUser(customerId, Role.CUSTOMER, "user", null, Set.of(), false);

            TransitionResult result = service.cancelAsOwner(parcelId, customerUser, "Customer cancel note");

            assertThat(result.status()).isEqualTo(ParcelStatus.CANCELLED);
            verify(parcelAccessPolicy).requireAccessible(parcelId, customerUser);
            verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        @Tag("TST-011")
        @DisplayName("BR-07: cancelAsOwner with reason > 300 returns 400 VALIDATION_ERROR")
        void cancelAsOwnerReasonTooLong() {
            Parcel parcel = createParcel(ParcelStatus.CREATED);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            CurrentUser customerUser = new CurrentUser(customerId, Role.CUSTOMER, "user", null, Set.of(), false);

            assertThatThrownBy(() -> service.cancelAsOwner(parcelId, customerUser, "A".repeat(301)))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("reason")));
        }

        @Test
        @Tag("TST-011")
        @DisplayName("BR-07: cancelAsAdmin requires reason 10-300 characters and records audit event")
        void cancelAsAdminSuccess() {
            Parcel parcel = createParcel(ParcelStatus.CREATED);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelRepository.saveAndFlush(any(Parcel.class))).thenReturn(parcel);

            CurrentUser admin = new CurrentUser(actorUserId, Role.ADMIN, "admin", null, Set.of(), false);

            TransitionResult result = service.cancelAsAdmin(parcelId, admin, "Reason with enough characters");

            assertThat(result.status()).isEqualTo(ParcelStatus.CANCELLED);
            verify(auditEvents).record(
                    actorUserId,
                    ActorRole.ADMIN,
                    "PARCEL_CANCELLED_BY_ADMIN",
                    "PARCEL",
                    parcelId.toString(),
                    Map.of("previousStatus", "CREATED"));
        }

        @Test
        @Tag("TST-011")
        @DisplayName("BR-07: cancelAsAdmin reason < 10 returns 400 VALIDATION_ERROR")
        void cancelAsAdminReasonTooShort() {
            CurrentUser admin = new CurrentUser(actorUserId, Role.ADMIN, "admin", null, Set.of(), false);

            assertThatThrownBy(() -> service.cancelAsAdmin(parcelId, admin, "Too short"))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("reason")));

            verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        @Tag("TST-011")
        @DisplayName("FR-PAR-012: failAsAdmin requires note 5-500, allowed reason, and records audit event")
        void failAsAdminSuccess() {
            Parcel parcel = createParcel(ParcelStatus.PICKED_UP);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelRepository.saveAndFlush(any(Parcel.class))).thenReturn(parcel);

            CurrentUser admin = new CurrentUser(actorUserId, Role.ADMIN, "admin", null, Set.of(), false);

            TransitionResult result = service.failAsAdmin(
                    parcelId, admin, FailureReason.PARCEL_DAMAGED, "Parcel was critically damaged");

            assertThat(result.status()).isEqualTo(ParcelStatus.FAILED);
            verify(auditEvents).record(
                    actorUserId,
                    ActorRole.ADMIN,
                    "PARCEL_FAILED_BY_ADMIN",
                    "PARCEL",
                    parcelId.toString(),
                    Map.of("previousStatus", "PICKED_UP", "failureReason", "PARCEL_DAMAGED"));
        }

        @Test
        @Tag("TST-011")
        @DisplayName("FR-PAR-012: failAsAdmin from CREATED is rejected with 409 INVALID_STATUS_TRANSITION")
        void failAsAdminFromCreatedRejected() {
            Parcel parcel = createParcel(ParcelStatus.CREATED);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            CurrentUser admin = new CurrentUser(actorUserId, Role.ADMIN, "admin", null, Set.of(), false);

            assertThatThrownBy(() -> service.failAsAdmin(
                    parcelId, admin, FailureReason.PARCEL_DAMAGED, "Force fail from created"))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).code()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION));

            verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
        }

        @Test
        @Tag("TST-011")
        @DisplayName("FR-PAR-012: failAsAdmin with disallowed reason returns 400 VALIDATION_ERROR")
        void failAsAdminDisallowedReason() {
            Parcel parcel = createParcel(ParcelStatus.PICKED_UP);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

            CurrentUser admin = new CurrentUser(actorUserId, Role.ADMIN, "admin", null, Set.of(), false);

            // SENDER_UNAVAILABLE_AT_PICKUP is not allowed for admin override (only PARCEL_DAMAGED, OTHER)
            assertThatThrownBy(() -> service.failAsAdmin(
                    parcelId, admin, FailureReason.SENDER_UNAVAILABLE_AT_PICKUP, "Admin forced fail"))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> assertThat(((DomainException) ex).errors()).anyMatch(e -> e.field().equals("failureReason")));

            verify(auditEvents, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("FR-PAR-003: Concurrency handling")
    class Concurrency {

        @Test
        @Tag("TST-011")
        @DisplayName("FR-PAR-003: Lost optimistic-lock update returns 409 CONCURRENT_UPDATE")
        void optimisticLockingFailureReturnsConcurrentUpdate() {
            Parcel parcel = createParcel(ParcelStatus.CREATED);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelRepository.saveAndFlush(any(Parcel.class)))
                    .thenThrow(new OptimisticLockingFailureException("Row updated by another transaction"));

            TransitionCommand command = new TransitionCommand(
                    parcelId, ParcelStatus.PICKED_UP, actorUserId, ActorRole.COURIER, null, null, null);

            assertThatThrownBy(() -> service.transition(command))
                    .isInstanceOf(DomainException.class)
                    .satisfies(ex -> {
                        DomainException de = (DomainException) ex;
                        assertThat(de.code()).isEqualTo(ErrorCode.CONCURRENT_UPDATE);
                    });
        }
    }

    @Nested
    @DisplayName("BR-28: Privacy and contact data omission")
    class PrivacyRules {

        @Test
        @DisplayName("BR-28: Outbox payload has no contact details (names, phones, emails, addresses)")
        void outboxPayloadOmitsContactData() {
            Parcel parcel = createParcel(ParcelStatus.OUT_FOR_DELIVERY);
            when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
            when(parcelRepository.saveAndFlush(any(Parcel.class))).thenReturn(parcel);

            TransitionCommand command = new TransitionCommand(
                    parcelId, ParcelStatus.DELIVERED, actorUserId, ActorRole.COURIER, "Private courier note", null, "Recipient Jane");

            service.transition(command);

            ArgumentCaptor<ParcelStatusChangedData> captor = ArgumentCaptor.forClass(ParcelStatusChangedData.class);
            verify(outboxService).add(eq(EventTypes.PARCEL_STATUS_CHANGED), captor.capture());

            ParcelStatusChangedData data = captor.getValue();
            // Data carries staff note
            assertThat(data.note()).isEqualTo("Private courier note");
            // toString() suppresses note from logs (BR-28)
            assertThat(data.toString()).doesNotContain("Private courier note");
            assertThat(data.toString()).doesNotContain("Recipient Jane");
        }
    }
}

