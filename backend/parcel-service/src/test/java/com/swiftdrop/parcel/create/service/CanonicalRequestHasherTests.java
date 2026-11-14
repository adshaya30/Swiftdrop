package com.swiftdrop.parcel.create.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("TST-012")
class CanonicalRequestHasherTests {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final CanonicalRequestHasher hasher = new CanonicalRequestHasher(mapper);

    @Test
    void matchesKnownSha256OfCanonicalJson() {
        assertThat(hasher.hash(Map.of())).isEqualTo(
                "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a");
    }

    @Test
    void recursivelySortsKeysAndNormalizesAllNumericForms() throws Exception {
        var first = mapper.readTree("{\"z\":[{\"b\":2.00,\"a\":1}],\"a\":1000.00,\"zero\":-0.0}");
        var second = mapper.readTree("{\"zero\":0,\"a\":1e3,\"z\":[{\"a\":1.0,\"b\":2}]}");
        assertThat(hasher.hash(first)).isEqualTo(hasher.hash(second)).matches("[0-9a-f]{64}");
    }

    @Test
    void preservesArrayOrderTypesNullAndAbsentProperties() {
        assertThat(hasher.hash(List.of(1, 2))).isNotEqualTo(hasher.hash(List.of(2, 1)));
        assertThat(hasher.hash(Map.of("weight", "2"))).isNotEqualTo(hasher.hash(Map.of("weight", 2)));
        var explicitNull = new LinkedHashMap<String, Object>();
        explicitNull.put("description", null);
        assertThat(hasher.hash(explicitNull)).isNotEqualTo(hasher.hash(Map.of()));
    }

    @Test
    void includesExpectedFeeMerchantReferenceAndEveryBodyField() {
        var body = new LinkedHashMap<String, Object>();
        body.put("expectedFee", new BigDecimal("800.00"));
        body.put("merchantReference", "REF-1");
        body.put("pickup", Map.of("city", "Colombo"));
        String original = hasher.hash(body);
        body.put("expectedFee", new BigDecimal("801.00"));
        assertThat(hasher.hash(body)).isNotEqualTo(original);
        body.put("expectedFee", new BigDecimal("800"));
        assertThat(hasher.hash(body)).isEqualTo(original);
        body.put("merchantReference", "REF-2");
        assertThat(hasher.hash(body)).isNotEqualTo(original);
        body.put("merchantReference", "REF-1");
        body.put("pickup", Map.of("city", "Kandy"));
        assertThat(hasher.hash(body)).isNotEqualTo(original);
    }

    @Test
    void retainsDecimalPrecisionAndUnicodeAndDoesNotMutateInput() {
        var body = new LinkedHashMap<String, Object>();
        body.put("z", new BigDecimal("0.123456789012345678901"));
        body.put("a", "සිංහල");
        var before = new LinkedHashMap<>(body);
        String original = hasher.hash(body);
        assertThat(body).isEqualTo(before);
        body.put("z", new BigDecimal("0.123456789012345678902"));
        assertThat(hasher.hash(body)).isNotEqualTo(original);
        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(NullPointerException.class);
    }
}
