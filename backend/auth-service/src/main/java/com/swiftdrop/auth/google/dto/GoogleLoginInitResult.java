package com.swiftdrop.auth.google.dto;

public record GoogleLoginInitResult(
        String authorizationUrl,
        String rawState
) {}

