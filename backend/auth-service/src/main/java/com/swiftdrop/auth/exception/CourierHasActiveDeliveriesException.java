package com.swiftdrop.auth.exception;

/**
 * Thrown when attempting to deactivate a courier who currently has in-progress deliveries.
 * SRS v2.1 – Flow 4.6, FR-AUTH-020
 */
public class CourierHasActiveDeliveriesException extends RuntimeException {
    public CourierHasActiveDeliveriesException(int activeCount) {
        super("Courier has " + activeCount + " active deliveries and cannot be deactivated.");
    }
}

