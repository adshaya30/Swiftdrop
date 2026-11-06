package com.swiftdrop.auth.google.service;

import com.swiftdrop.auth.google.dto.GoogleUserClaims;

public interface GoogleOidcVerifier {
    GoogleUserClaims verifyIdToken(String idTokenString, String expectedNonce);
}
