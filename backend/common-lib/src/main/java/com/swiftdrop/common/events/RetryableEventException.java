package com.swiftdrop.common.events;

/**
 * The event cannot be processed yet, for example a {@code parcel.status.changed} for a parcel the consumer does not
 * know yet (SRS 3.5: ordering gaps resolve through the retry policy). Throw it from a handler: the message is retried
 * and, if it still fails after the last attempt, dead-lettered like any other failure.
 */
public class RetryableEventException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RetryableEventException(String message) {
        super(message);
    }

    public RetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
