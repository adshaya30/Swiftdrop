package com.swiftdrop.auth.dto.response;

/**
 * Public authentication configuration.
 * SRS v2.1 – FR-AUTH-011
 *
 * Exposes feature flags without revealing client secrets, signing keys,
 * or internal infrastructure configuration.
 */
public record AuthConfigResponse(boolean googleEnabled) {}

