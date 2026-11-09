package com.swiftdrop.delivery.messaging;

import com.rabbitmq.client.Channel;
import com.swiftdrop.common.events.EventConsumerSupport;
import com.swiftdrop.common.events.EventTopology;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryEventsListener {

    private final EventConsumerSupport consumerSupport;
    private final DeliveryEventHandler eventHandler;

    public DeliveryEventsListener(EventConsumerSupport consumerSupport, DeliveryEventHandler eventHandler) {
        this.consumerSupport = consumerSupport;
        this.eventHandler = eventHandler;
    }

    @RabbitListener(queues = EventTopology.DELIVERY_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        consumerSupport.handle(message, channel, eventHandler::handle);
    }
}