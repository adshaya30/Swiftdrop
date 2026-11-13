package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MockSmsSenderTest {
    @Test
    void logsSmsWithMaskedDestination() {
        Logger logger = (Logger) LoggerFactory.getLogger(MockSmsSender.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            new MockSmsSender().send("+94771234567", "Parcel SD1234567890 created. Track: https://public.example/track/SD1234567890");
            String message = appender.list.get(0).getFormattedMessage();
            assertThat(message).contains("+94*****4567", "SD1234567890");
            assertThat(message).doesNotContain("+94771234567");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
