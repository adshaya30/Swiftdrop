package com.swiftdrop.notification.service;

/**
 * FR-NTF-002: SMS sending interface.
 */
public interface SmsSender {
    void send(String to, String body) throws Exception;
}

