# Events: how to publish, consume, audit and test

For everyone who publishes or consumes SwiftDrop events. The contract is SRS 3.5. All of this comes with the
`common-lib` dependency: no `@EnableRabbit`, `@EntityScan` or extra beans are needed. Package: `com.swiftdrop.common.events`.

| Service | Publishes (`outbox_events` table) | Consumes |
|---|---|---|
| auth, parcel, pricing | yes | no |
| delivery | yes | `delivery.events`, with `processed_events` |
| tracking | no | `tracking.events`, with `processed_events` |
| notification | no | `notification.events`, de-duplicates with its own unique keys (FR-NTF-004, FR-WHK-006) |

Copy the `outbox_events` and `processed_events` table definitions of SRS 6.1 into your service's V1 migration.

## Publish an event

Call `OutboxService.add` inside the transaction of the business change. The event is stored with that change, and
`OutboxPublisher` sends it to RabbitMQ after the commit, so a down broker never fails the business operation.

```java
@Transactional
public void approve(UUID merchantId) {
    // ... business change ...
    outboxService.add(EventTypes.MERCHANT_STATUS_CHANGED,
            new MerchantStatusChangedData(merchantId, businessName, MerchantStatus.PENDING, MerchantStatus.APPROVED, null));
}
```

- The first argument is an `EventTypes` constant, and the data must be its record from `events.data`. Anything else is
  rejected with `IllegalArgumentException`.
- Outside a transaction it throws `IllegalTransactionStateException`.
- Events carry no customer, merchant or recipient contact data (BR-28).
- `OUTBOX_POLL_INTERVAL_MS` sets how often the publisher runs (default 1000).

## Audit an action

```java
auditEvents.record("PRICE_CHANGED", "ZONE_PRICING", id, Map.of("baseFee", "500.00"));          // actor = current user
auditEvents.record(null, ActorRole.SYSTEM, "COURIER_UNASSIGNED", "DELIVERY", id, Map.of());    // explicit actor
```

Same transaction rule as above. Detail keys such as `password`, `secret`, `token`, `email`, `phone`, `address` or
`fullName` throw `IllegalArgumentException`, also inside nested maps and lists. `spring.application.name` must be set.

## Consume events

Use two beans: a listener that calls `EventConsumerSupport`, and a handler with the `@Transactional` method.

```java
@Component
class DeliveryEventsListener {
    private final EventConsumerSupport consumer;
    private final DeliveryEventHandler handler;
    // constructor ...

    @RabbitListener(queues = EventTopology.DELIVERY_QUEUE)
    void onMessage(Message message, Channel channel) throws IOException {
        consumer.handle(message, channel, handler::handle);
    }
}

@Service
class DeliveryEventHandler {
    private final ProcessedEvents processedEvents;
    // constructor ...

    @Transactional
    public void handle(EventEnvelope<?> event) {
        if (!processedEvents.firstTime(event.eventId())) {
            return;   // already processed: returning normally acknowledges the message
        }
        switch (event.data()) {
            case ParcelCreatedData d -> create(d);
            case ParcelStatusChangedData d -> applyStatus(d);
            case CourierChangedData d -> upsertCourier(d);
            default -> { }
        }
    }

    private void applyStatus(ParcelStatusChangedData d) {
        Delivery delivery = repository.findById(d.parcelId())
                .orElseThrow(() -> new RetryableEventException("Parcel not known yet"));
        if (d.sequence() <= delivery.getStatusSequence()) {
            return;   // stale: ignore it (SRS 3.5); each service does this check itself
        }
        // ... apply ...
    }
}
```

What happens to a message:

| Outcome | Result |
|---|---|
| The handler returns | Acknowledged |
| The handler throws (any exception, including `RetryableEventException`) | 3 attempts in total, waiting 1 s and then 5 s; then moved to `<queue>.dlq`, kept 3 days |
| The message cannot be read as an event | Moved to `<queue>.dlq` at once, the handler is not called |
| The service shuts down during a retry wait | Put back on the queue |

Rules:

- Do **not** add `containerFactory` or call `basicAck`/`basicNack` yourself. Every `@RabbitListener` already runs with
  manual acknowledgement and prefetch 10, and `EventConsumerSupport` acknowledges for you.
- Keep `@Transactional` on a **different bean** from the listener. Each attempt then has its own transaction, and a failed
  attempt rolls back its `processed_events` row, so the retry is not mistaken for a duplicate.
- `ProcessedEvents.firstTime` must run inside that transaction. notification-service does not use it.
- Never log event data or exception messages that can contain personal data or the staff note (BR-28).
- The request id of the original request is in the MDC as `requestId` during handling, and is carried on to any event
  you publish from the handler.

## Test

- Set `swiftdrop.events.retry.initial-delay-ms=1` in test properties to shorten the 1 s and 5 s waits (the second wait is
  always five times the first). It is for tests only; do not set it in production.
- Use Testcontainers with `rabbitmq:4-management-alpine` and Awaitility, never `Thread.sleep`.
- Publish a test event by sending the JSON envelope to `EventTopology.EXCHANGE` with the event type as routing key.
- In a Mockito unit test, set `TransactionSynchronizationManager.setActualTransactionActive(true)` before calling
  `OutboxService` or `ProcessedEvents`, and reset it afterwards.
- `EventConsumerFlowTest` in this module is a full working example.

## Common problems

| Symptom | Cause |
|---|---|
| `IllegalTransactionStateException` | The call is not inside a `@Transactional` method, or the method is called from the same class so the proxy is skipped |
| Log line "Outbox publishing run failed" every second | The `outbox_events` table does not match SRS 6.1, or the database is down |
| Log line "No outbox_events table" | Normal in tracking-service and notification-service |
| Retries are treated as duplicates | `@Transactional` is on the listener bean or missing, so the `processed_events` row is not rolled back |
| `Could not find a valid Docker environment` when running tests | Docker Desktop is not running |
