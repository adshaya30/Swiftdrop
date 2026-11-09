package com.swiftdrop.common.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Reads an X.509 "BEGIN PUBLIC KEY" PEM file from JWT_PUBLIC_KEY_PATH. */
public class FileJwtPublicKeyProvider implements JwtPublicKeyProvider {

    private final String path;
    private volatile RSAPublicKey cached;

    public FileJwtPublicKeyProvider(String path) {
        this.path = path;
    }

    @Override
    public RSAPublicKey publicKey() {
        if (cached == null) {
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("JWT_PUBLIC_KEY_PATH is not set");
            }
            try {
                cached = parse(Files.readString(Path.of(path), StandardCharsets.US_ASCII));
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read the JWT public key file", e);
            }
        }
        return cached;
    }

    /** Parses a PEM encoded X.509 RSA public key. */
    public static RSAPublicKey parse(String pem) {
        try {
            String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Invalid JWT public key (expected X.509 PEM, RSA)", e);
        }
    }
}
