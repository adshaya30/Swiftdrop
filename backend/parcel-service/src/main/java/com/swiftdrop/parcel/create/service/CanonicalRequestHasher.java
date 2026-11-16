package com.swiftdrop.parcel.create.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * FR-PAR-006: SHA-256 of the complete validated request body, including expectedFee.
 * Object keys sort recursively; arrays retain order; decimal scales are normalized.
 * Explicit null and absent properties remain distinct. No contact data is logged.
 */
@Component
public class CanonicalRequestHasher {
    private final ObjectMapper mapper;

    public CanonicalRequestHasher(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String hash(Object validatedBody) {
        Objects.requireNonNull(validatedBody, "validatedBody");
        JsonNode body = mapper.valueToTree(validatedBody);
        byte[] canonical = canonicalize(body).toString().getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(e -> fields.put(e.getKey(), e.getValue()));
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            fields.forEach((key, value) -> result.set(key, canonicalize(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        if (node.isNumber()) {
            return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        }
        return node;
    }
}
