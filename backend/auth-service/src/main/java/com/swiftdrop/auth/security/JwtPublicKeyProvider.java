package com.swiftdrop.auth.security;

import java.security.interfaces.RSAPublicKey;

/**
 * Public key provider contract per Master Prompt Section 8.1.
 * // TODO(common): replace with com.swiftdrop.common.security.JwtPublicKeyProvider from common-lib once published.
 */
public interface JwtPublicKeyProvider {
    RSAPublicKey getPublicKey();
}
