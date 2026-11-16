package com.swiftdrop.parcel.create.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.common.enums.OwnerType;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.parcel.domain.Parcel;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import static com.swiftdrop.parcel.support.ParcelFixtures.parcel;
import static org.assertj.core.api.Assertions.*;

/** Master 8.1 / SRS 7.4: shared DTO contracts, not admin or merchant endpoint implementations. */
class ParcelDetailMapperTests {
    private final ParcelDetailMapper mapper = Mappers.getMapper(ParcelDetailMapper.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @EnumSource(OwnerType.class)
    void allViewsPreserveSnapshotsAndAddOnlyTheirAgreedFields(OwnerType ownerType) {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Parcel entity = parcel("SD-0123456789",
                ownerType == OwnerType.CUSTOMER ? owner : null,
                ownerType == OwnerType.MERCHANT ? owner : null,
                ownerType == OwnerType.MERCHANT ? "REF-1" : null);
        Instant created = Instant.parse("2026-11-11T00:00:00Z");
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "createdAt", created);
        ReflectionTestUtils.setField(entity, "updatedAt", created.plusSeconds(60));
        entity.setStatus(ParcelStatus.PICKED_UP);
        entity.setStatusSequence(2);

        var customer = mapper.toDetail(entity);
        var admin = mapper.toAdminDetail(entity);
        var internal = mapper.toInternalDetail(entity);
        assertThat(customer.id()).isEqualTo(id);
        assertThat(customer.ownerType()).isEqualTo(ownerType);
        assertThat(customer.status()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(customer.createdAt()).isEqualTo(created);
        assertThat(customer.updatedAt()).isEqualTo(created.plusSeconds(60));
        assertThat(customer.pickup().contactPhone()).isEqualTo("+94770000001");
        assertThat(customer.pickup().districtName()).isEqualTo("Colombo");
        assertThat(customer.dropoff().contactEmail()).isEqualTo("test@example.invalid");
        assertThat(customer.dropoff().postalCode()).isEqualTo("20000");
        assertThat(customer.parcel().weightKg()).isEqualByComparingTo("2.00");
        assertThat(customer.originZone().code()).isEqualTo("WESTERN");
        assertThat(customer.destinationZone().name()).isEqualTo("Central");
        assertThat(customer.fee().baseFee()).isEqualByComparingTo("500.00");
        assertThat(customer.fee().weightFee()).isEqualByComparingTo("150.00");
        assertThat(customer.fee().sizeSurcharge()).isEqualByComparingTo("150.00");
        assertThat(customer.fee().discountAmount()).isEqualByComparingTo("0.00");
        assertThat(customer.fee().total()).isEqualByComparingTo("800.00");
        assertThat(customer.fee().currency()).isEqualTo("LKR");
        assertThat(admin.customerId()).isEqualTo(entity.getCustomerId());
        assertThat(admin.merchantId()).isEqualTo(entity.getMerchantId());
        assertThat(internal.customerId()).isEqualTo(entity.getCustomerId());
        assertThat(internal.merchantId()).isEqualTo(entity.getMerchantId());
        assertThat(internal.statusSequence()).isEqualTo(2);

        ObjectNode customerJson = json.valueToTree(customer);
        ObjectNode adminJson = json.valueToTree(admin);
        ObjectNode internalJson = json.valueToTree(internal);
        Set<String> customerFields = fields(customerJson);
        assertThat(customerFields).containsExactlyInAnyOrder("id", "trackingNumber", "status", "ownerType",
                "merchantReference", "pickup", "dropoff", "parcel", "deliveryInstructions",
                "originZone", "destinationZone", "fee", "createdAt", "updatedAt");
        Set<String> adminFields = new HashSet<>(customerFields);
        adminFields.addAll(Set.of("customerId", "merchantId"));
        assertThat(fields(adminJson)).isEqualTo(adminFields);
        Set<String> internalFields = new HashSet<>(adminFields);
        internalFields.add("statusSequence");
        assertThat(fields(internalJson)).isEqualTo(internalFields);
        internalJson.remove("statusSequence");
        assertThat(internalJson).isEqualTo(adminJson);
        adminJson.remove("customerId");
        adminJson.remove("merchantId");
        assertThat(adminJson).isEqualTo(customerJson);
    }

    @Test
    void mapsMissingEntitiesToNullWithoutInventingData() {
        assertThat(mapper.toDetail(null)).isNull();
        assertThat(mapper.toAdminDetail(null)).isNull();
        assertThat(mapper.toInternalDetail(null)).isNull();
    }

    private Set<String> fields(ObjectNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
