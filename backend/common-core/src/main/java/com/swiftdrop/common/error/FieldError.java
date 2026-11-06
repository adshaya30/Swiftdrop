package com.swiftdrop.common.error;

/** One entry of the errors[] member of a Problem Details body. */
public record FieldError(String field, String message) {
}
