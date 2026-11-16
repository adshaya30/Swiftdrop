package com.swiftdrop.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {
    @Test
    void sendsPlainTextMessageThroughConfiguredSmtpSender() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, "notifications@swiftdrop.example");

        sender.send("customer@example.test", "SwiftDrop: Parcel Created", "Parcel SD1234567890\nTrack: https://public.example/track/SD1234567890");

        ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captured.capture());
        assertThat(captured.getValue().getFrom()).isEqualTo("notifications@swiftdrop.example");
        assertThat(captured.getValue().getTo()).containsExactly("customer@example.test");
        assertThat(captured.getValue().getSubject()).isEqualTo("SwiftDrop: Parcel Created");
        assertThat(captured.getValue().getText()).contains("SD1234567890", "https://public.example/track/");
    }
}
