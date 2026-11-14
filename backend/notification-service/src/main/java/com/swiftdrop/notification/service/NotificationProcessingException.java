package com.swiftdrop.notification.service;

/** A deliberately data-free exception safe to classify in consumer logs. */
public final class NotificationProcessingException extends RuntimeException {
    public NotificationProcessingException() {
        super("Notification processing failed");
    }
}
