package com.swiftdrop.common.security;

import java.security.interfaces.RSAPublicKey;

/**
 * Supplies the RS256 public key. The default reads JWT_PUBLIC_KEY_PATH; auth-service overrides it by deriving
 * the public key from its private key.
 */
@FunctionalInterface
public interface JwtPublicKeyProvider {

    RSAPublicKey publicKey();
}
