package com.swiftdrop.notification.service;

/**
 * FR-NTF-002: Email sending interface.
 */
public interface EmailSender {
    void send(String to, String subject, String body) throws Exception;
}

