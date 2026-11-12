package com.swiftdrop.parcel.create.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.parcel.create.service.CanonicalRequestHasher;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;

@Tag("TST-012")
class CustomerParcelCreationRequestTests {
    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void initialize() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() { factory.close(); }

    private ObjectNode body() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"pickup":{"contactName":"Sender","contactPhone":"0771234567",
                "addressLine1":"12 Road","city":"Colombo","districtCode":"COLOMBO"},
                "dropoff":{"contactName":"Receiver","contactPhone":"+94712345678",
                "addressLine1":"45 Road","city":"Kandy","districtCode":"KANDY"},
                "parcel":{"weightKg":2.00,"size":"MEDIUM"},"expectedFee":800.00}
                """);
    }

    private CustomerParcelCreationRequest request(ObjectNode body) throws Exception {
        return mapper.treeToValue(body, CustomerParcelCreationRequest.class);
    }

    @Test
    void acceptsOptionalFieldsAndNormalizesPhonesBeforeHashing() throws Exception {
        var local = request(body());
        assertThat(validator.validate(local)).isEmpty();
        assertThat(validator.validate(local.toParcelInput())).isEmpty();
        assertThat(local.pickup().contactPhone()).isEqualTo("+94771234567");
        var alternate = body();
        ((ObjectNode) alternate.get("pickup")).put("contactPhone", "+94 77 123 4567");
        assertThat(new CanonicalRequestHasher(mapper).hash(local))
                .isEqualTo(new CanonicalRequestHasher(mapper).hash(request(alternate)));
        assertThat(local.toParcelInput().parcel()).isEqualTo(local.parcel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1.00", "1.01", "30.01", "1000.00"})
    void validatesMinimumAndFormatWithoutOwningTheMaximum(String weight) throws Exception {
        var body = body();
        ((ObjectNode) body.get("parcel")).put("weightKg", new java.math.BigDecimal(weight));
        assertThat(validator.validate(request(body))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SMALL", "MEDIUM", "LARGE"})
    void acceptsEachSizeAndValidOptionalContactFields(String size) throws Exception {
        var body = body();
        ((ObjectNode) body.get("parcel")).put("size", size);
        ((ObjectNode) body.get("pickup")).put("contactName", "AB");
        ((ObjectNode) body.get("pickup")).put("contactPhone", "0112345678");
        ((ObjectNode) body.get("dropoff")).put("contactEmail", "receiver@example.com");
        assertThat(validator.validate(request(body))).isEmpty();
        assertThat(request(body).pickup().contactPhone()).isEqualTo("+94112345678");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "0.001", "2.001", "1.000"})
    void rejectsWeightMinimumOrExcessScale(String weight) throws Exception {
        // Construct directly to retain the submitted BigDecimal scale.
        var valid = request(body());
        var invalid = new CustomerParcelCreationRequest(valid.pickup(), valid.dropoff(),
                new ParcelInput.ParcelData(new java.math.BigDecimal(weight), valid.parcel().size(), null),
                null, valid.expectedFee());
        assertThat(validator.validate(invalid)).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("parcel.weightKg"));
    }

    static Stream<Arguments> invalidFields() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (String section : new String[]{"pickup", "dropoff"}) {
            for (String field : new String[]{"contactName", "contactPhone", "addressLine1", "city", "districtCode"}) {
                cases.add(Arguments.of(section, field, null));
                cases.add(Arguments.of(section, field, " "));
            }
            cases.add(Arguments.of(section, "contactName", "A"));
            cases.add(Arguments.of(section, "contactName", "a".repeat(101)));
            cases.add(Arguments.of(section, "contactPhone", "+44123456789"));
            cases.add(Arguments.of(section, "contactPhone", "077123"));
            cases.add(Arguments.of(section, "addressLine1", "a".repeat(151)));
            cases.add(Arguments.of(section, "addressLine2", "a".repeat(151)));
            cases.add(Arguments.of(section, "city", "a".repeat(81)));
            cases.add(Arguments.of(section, "districtCode", "a".repeat(21)));
            cases.add(Arguments.of(section, "postalCode", "1234"));
            cases.add(Arguments.of(section, "postalCode", "123456"));
            cases.add(Arguments.of(section, "postalCode", "ABCDE"));
        }
        cases.add(Arguments.of("dropoff", "contactEmail", "invalid"));
        cases.add(Arguments.of("dropoff", "contactEmail", "a".repeat(250) + "@x.lk"));
        cases.add(Arguments.of("parcel", "description", "a".repeat(201)));
        cases.add(Arguments.of("", "deliveryInstructions", "a".repeat(301)));
        return cases.build();
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void reportsNestedFieldViolations(String section, String field, String value) throws Exception {
        var body = body();
        ObjectNode target = section.isEmpty() ? body : (ObjectNode) body.get(section);
        if (value == null) target.putNull(field); else target.put(field, value);
        String path = section.isEmpty() ? field : section + "." + field;
        assertThat(validator.validate(request(body))).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo(path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pickup", "dropoff", "parcel", "expectedFee"})
    void requiresTopLevelValues(String field) throws Exception {
        var body = body();
        body.remove(field);
        assertThat(validator.validate(request(body))).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo(field));
    }

    @ParameterizedTest
    @ValueSource(strings = {"weightKg", "size"})
    void requiresParcelValues(String field) throws Exception {
        var body = body();
        ((ObjectNode) body.get("parcel")).remove(field);
        assertThat(validator.validate(request(body))).anySatisfy(v ->
                assertThat(v.getPropertyPath().toString()).isEqualTo("parcel." + field));
    }

    @Test
    void acceptsMaximumLengthsAndRejectsUnknownPropertiesAndSizes() throws Exception {
        var body = body();
        for (String section : new String[]{"pickup", "dropoff"}) {
            ObjectNode address = (ObjectNode) body.get(section);
            address.put("contactName", "a".repeat(100));
            address.put("addressLine1", "a".repeat(150));
            address.put("addressLine2", "a".repeat(150));
            address.put("city", "a".repeat(80));
            address.put("districtCode", "a".repeat(20));
            address.put("postalCode", "00300");
        }
        ((ObjectNode) body.get("parcel")).put("description", "a".repeat(200));
        body.put("deliveryInstructions", "a".repeat(300));
        assertThat(validator.validate(request(body))).isEmpty();
        body.put("customerId", "untrusted");
        assertThatThrownBy(() -> request(body)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        body.remove("customerId");
        ((ObjectNode) body.get("parcel")).put("size", "HUGE");
        assertThatThrownBy(() -> request(body)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }
}
