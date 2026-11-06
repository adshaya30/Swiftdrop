package com.swiftdrop.common.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("TST-013")
class EnumsTest {

    @Test
    @DisplayName("TST-013 SRS 6.2: parcel status labels and terminal states (Table 2.3)")
    void parcelStatus() {
        assertThat(ParcelStatus.values()).extracting(ParcelStatus::label).containsExactly(
                "Created", "Picked up", "In transit", "Out for delivery", "Delivered", "Delivery failed", "Cancelled");
        assertThat(EnumSet.allOf(ParcelStatus.class).stream().filter(ParcelStatus::isTerminal))
                .containsExactlyInAnyOrder(ParcelStatus.DELIVERED, ParcelStatus.FAILED, ParcelStatus.CANCELLED);
        assertThat(ParcelStatus.OUT_FOR_DELIVERY.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("TST-013 SRS 6.2: the seven failure reason labels")
    void failureReasonLabels() {
        assertThat(FailureReason.values()).hasSize(7);
        assertThat(FailureReason.RECIPIENT_UNAVAILABLE.label()).isEqualTo("Recipient not available");
        assertThat(FailureReason.WRONG_OR_INCOMPLETE_ADDRESS.label()).isEqualTo("Wrong or incomplete address");
        assertThat(FailureReason.RECIPIENT_REFUSED.label()).isEqualTo("Recipient refused the parcel");
        assertThat(FailureReason.SENDER_UNAVAILABLE_AT_PICKUP.label()).isEqualTo("Sender not available at pickup");
        assertThat(FailureReason.PARCEL_DAMAGED.label()).isEqualTo("Parcel damaged");
        assertThat(FailureReason.ACCESS_RESTRICTED.label()).isEqualTo("Location not accessible");
        assertThat(FailureReason.OTHER.label()).isEqualTo("Other reason");
    }

    @Test
    @DisplayName("TST-013 Table 2.4: failure reasons allowed per stage")
    void allowedFor() {
        assertThat(FailureReason.allowedFor(ParcelStatus.CREATED, false)).containsExactlyInAnyOrder(
                FailureReason.SENDER_UNAVAILABLE_AT_PICKUP, FailureReason.WRONG_OR_INCOMPLETE_ADDRESS,
                FailureReason.ACCESS_RESTRICTED, FailureReason.OTHER);
        assertThat(FailureReason.allowedFor(ParcelStatus.PICKED_UP, false))
                .containsExactlyInAnyOrder(FailureReason.PARCEL_DAMAGED, FailureReason.OTHER);
        assertThat(FailureReason.allowedFor(ParcelStatus.IN_TRANSIT, false))
                .containsExactlyInAnyOrder(FailureReason.PARCEL_DAMAGED, FailureReason.OTHER);
        assertThat(FailureReason.allowedFor(ParcelStatus.OUT_FOR_DELIVERY, false)).containsExactlyInAnyOrder(
                FailureReason.RECIPIENT_UNAVAILABLE, FailureReason.WRONG_OR_INCOMPLETE_ADDRESS,
                FailureReason.RECIPIENT_REFUSED, FailureReason.PARCEL_DAMAGED, FailureReason.ACCESS_RESTRICTED,
                FailureReason.OTHER);
    }

    @Test
    @DisplayName("TST-013 Table 2.4 / FR-PAR-012: admin override and terminal or missing stages")
    void allowedForAdminAndOthers() {
        for (ParcelStatus s : new ParcelStatus[] {ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
                ParcelStatus.OUT_FOR_DELIVERY}) {
            assertThat(FailureReason.allowedFor(s, true))
                    .containsExactlyInAnyOrder(FailureReason.PARCEL_DAMAGED, FailureReason.OTHER);
        }
        assertThat(FailureReason.allowedFor(ParcelStatus.CREATED, true)).isEmpty();
        assertThat(FailureReason.allowedFor(ParcelStatus.DELIVERED, false)).isEmpty();
        assertThat(FailureReason.allowedFor(ParcelStatus.CANCELLED, true)).isEmpty();
        assertThat(FailureReason.allowedFor(null, false)).isEmpty();
    }

    @Test
    @DisplayName("TST-013 SRS 6.2: the remaining enumerations have exactly the specified values")
    void otherEnums() {
        assertThat(Role.values()).extracting(Enum::name).containsExactly("CUSTOMER", "MERCHANT", "COURIER", "ADMIN");
        assertThat(MerchantStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "APPROVED", "REJECTED", "SUSPENDED");
        assertThat(ParcelSize.values()).extracting(Enum::name).containsExactly("SMALL", "MEDIUM", "LARGE");
        assertThat(VehicleType.values()).extracting(Enum::name).containsExactly("MOTORBIKE", "THREE_WHEELER", "VAN");
        assertThat(ActorRole.values()).extracting(Enum::name)
                .containsExactly("CUSTOMER", "MERCHANT", "COURIER", "ADMIN", "SYSTEM");
        assertThat(NotificationChannel.values()).extracting(Enum::name).containsExactly("EMAIL", "SMS");
        assertThat(RecipientKind.values()).extracting(Enum::name).containsExactly("SENDER", "RECIPIENT", "MERCHANT");
        assertThat(NotificationStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "SENT", "FAILED", "SKIPPED");
        assertThat(WebhookDeliveryStatus.values()).extracting(Enum::name)
                .containsExactly("PENDING", "SUCCEEDED", "FAILED");
        assertThat(UserStatus.values()).extracting(Enum::name).containsExactly("ACTIVE", "DEACTIVATED");
        assertThat(AuthProvider.values()).extracting(Enum::name).containsExactly("LOCAL", "GOOGLE");
        assertThat(CredentialStatus.values()).extracting(Enum::name).containsExactly("ACTIVE", "REVOKED");
        assertThat(OwnerType.values()).extracting(Enum::name).containsExactly("CUSTOMER", "MERCHANT");
    }
}
