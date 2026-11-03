package com.swiftdrop.auth.exception;

/**
 * Thrown when delivery-service is unreachable or times out during courier status verification.
 * SRS v2.1 – Flow 4.6, FR-AUTH-020
 */
public class DeliveryServiceUnavailableException extends RuntimeException {
    public DeliveryServiceUnavailableException(String message) {
        super(message);
    }

    public DeliveryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

