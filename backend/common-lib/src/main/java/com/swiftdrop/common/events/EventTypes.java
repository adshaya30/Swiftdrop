package com.swiftdrop.common.events;

import com.swiftdrop.common.events.data.AuditRecordedData;
import com.swiftdrop.common.events.data.CourierChangedData;
import com.swiftdrop.common.events.data.DeliveryAssignedData;
import com.swiftdrop.common.events.data.DeliveryUnassignedData;
import com.swiftdrop.common.events.data.MerchantStatusChangedData;
import com.swiftdrop.common.events.data.ParcelCreatedData;
import com.swiftdrop.common.events.data.ParcelStatusChangedData;
import java.util.Map;
import java.util.Set;

/** Event types of SRS 3.5. The event type always equals the routing key. */
public final class EventTypes {

    public static final String PARCEL_CREATED = "parcel.created";
    public static final String PARCEL_STATUS_CHANGED = "parcel.status.changed";
    public static final String DELIVERY_ASSIGNED = "delivery.assigned";
    public static final String DELIVERY_UNASSIGNED = "delivery.unassigned";
    public static final String COURIER_CHANGED = "courier.changed";
    public static final String MERCHANT_STATUS_CHANGED = "merchant.status.changed";
    public static final String AUDIT_RECORDED = "audit.recorded";

    private static final Map<String, Class<?>> DATA_TYPES = Map.of(
            PARCEL_CREATED, ParcelCreatedData.class,
            PARCEL_STATUS_CHANGED, ParcelStatusChangedData.class,
            DELIVERY_ASSIGNED, DeliveryAssignedData.class,
            DELIVERY_UNASSIGNED, DeliveryUnassignedData.class,
            COURIER_CHANGED, CourierChangedData.class,
            MERCHANT_STATUS_CHANGED, MerchantStatusChangedData.class,
            AUDIT_RECORDED, AuditRecordedData.class);

    /** All seven event types (= routing keys). */
    public static final Set<String> ALL = DATA_TYPES.keySet();

    private EventTypes() {
    }

    public static boolean isKnown(String eventType) {
        return eventType != null && DATA_TYPES.containsKey(eventType);
    }

    /** The data record class carried by an event type; unknown types are rejected. */
    public static Class<?> dataType(String eventType) {
        Class<?> type = eventType == null ? null : DATA_TYPES.get(eventType);
        if (type == null) {
            throw new IllegalArgumentException("Unknown event type: " + eventType);
        }
        return type;
    }
}
