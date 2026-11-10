package com.swiftdrop.notification.service;

import com.swiftdrop.common.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FR-NTF-002: Mock SMS sender (SMS_PROVIDER=mock).
 * Logs an INFO line with the masked number. The message body is stored
 * in notifications.body_preview.
 */
@Component
public class MockSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

    @Override
    public void send(String to, String body) {
        // Log with masked number only (FR-NTF-006, BR-28)
        log.info("SMS sent to {} : {}", MaskingUtil.maskPhone(to), body);
    }
}

