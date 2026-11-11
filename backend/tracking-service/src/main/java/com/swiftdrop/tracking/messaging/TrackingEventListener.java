package com.swiftdrop.tracking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.swiftdrop.common.events.EventConsumerSupport;
import com.swiftdrop.common.events.EventTopology;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * RabbitMQ listener for the tracking.events queue (FR-TRK-001, SRS 3.5).
 */
@Component
public class TrackingEventListener {

    private final EventConsumerSupport consumer;
    private final TrackingEventHandlers handlers;

    public TrackingEventListener(EventConsumerSupport consumer, TrackingEventHandlers handlers) {
        this.consumer = consumer;
        this.handlers = handlers;
    }

    @RabbitListener(queues = EventTopology.TRACKING_QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.handle(message, channel, handlers::handle);
    }
}

