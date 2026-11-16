package com.swiftdrop.notification.messaging;

import com.rabbitmq.client.Channel;
import com.swiftdrop.common.events.EventConsumerSupport;
import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * RabbitMQ listener for notification.events queue (FR-NTF-001, SRS 3.5).
 */
@Component
public class NotificationEventListener {

    private final EventConsumerSupport consumer;
    private final NotificationEventHandlers handlers;

    public NotificationEventListener(EventConsumerSupport consumer,
                                      NotificationEventHandlers handlers) {
        this.consumer = consumer;
        this.handlers = handlers;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATION_QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.handle(message, channel, handlers::handle);
    }
}

