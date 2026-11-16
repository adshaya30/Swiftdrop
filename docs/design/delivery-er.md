# Delivery Service Entity-Relationship Diagram

The schema below is derived from delivery-service `V1__init.sql` and the JPA entities. Solid relationships are enforced by database foreign keys. UUID references explicitly marked as conceptual have no foreign key in this database.

```mermaid
erDiagram
    couriers ||--o{ deliveries : "currently assigned (courier_id nullable)"
    deliveries ||--o{ delivery_assignments : "assignment history"
    couriers ||--o{ delivery_assignments : "assigned courier"
    deliveries ||--o| delivery_proofs : "has proof"
    delivery_proofs ||--o| proof_photos : "may include photo"

    deliveries {
        uuid parcel_id PK "PK; parcel-service identifier, no cross-service FK"
        varchar tracking_number UK "NOT NULL UNIQUE"
        varchar status "NOT NULL; parcel lifecycle status"
        int status_sequence "NOT NULL"
        varchar owner_type "NOT NULL; CUSTOMER or MERCHANT"
        uuid customer_id "Nullable; conceptual auth-service reference, no FK"
        uuid merchant_id "Nullable; conceptual auth-service reference, no FK"
        varchar origin_district_code "NOT NULL"
        varchar origin_district_name "NOT NULL"
        varchar destination_district_code "NOT NULL"
        varchar destination_district_name "NOT NULL"
        varchar pickup_city "NOT NULL"
        varchar dropoff_city "NOT NULL"
        uuid courier_id FK "Nullable; references couriers.id"
        timestamptz assigned_at "Nullable"
        timestamptz parcel_created_at "NOT NULL"
        timestamptz terminal_at "Nullable"
        timestamptz created_at "NOT NULL"
        timestamptz updated_at "NOT NULL"
        bigint version "NOT NULL; optimistic version"
    }

    couriers {
        uuid id PK "PK; mirrors courier profile id, no cross-service FK"
        varchar full_name "NOT NULL"
        varchar vehicle_type "NOT NULL"
        varchar vehicle_number "NOT NULL"
        varchar status "NOT NULL; ACTIVE or DEACTIVATED"
        timestamptz updated_at "NOT NULL"
    }

    delivery_assignments {
        uuid id PK "PK"
        uuid parcel_id FK "NOT NULL; references deliveries.parcel_id"
        uuid courier_id FK "NOT NULL; references couriers.id"
        uuid assigned_by "NOT NULL; conceptual user id, no FK"
        timestamptz assigned_at "NOT NULL"
        timestamptz unassigned_at "Nullable until closed"
    }

    delivery_proofs {
        uuid parcel_id PK, FK "PK and FK to deliveries.parcel_id"
        varchar recipient_name "NOT NULL"
        varchar note "Nullable"
        boolean has_photo "NOT NULL"
        timestamptz captured_at "NOT NULL"
    }

    proof_photos {
        uuid parcel_id PK, FK "PK and FK to delivery_proofs.parcel_id"
        varchar content_type "NOT NULL; image/jpeg"
        bytea data "NOT NULL"
    }

    processed_events {
        uuid event_id PK "PK; consumed event id"
        timestamptz processed_at "NOT NULL"
    }

    outbox_events {
        uuid id PK "PK; event id"
        varchar event_type "NOT NULL"
        varchar routing_key "NOT NULL"
        jsonb payload "NOT NULL"
        uuid correlation_id "Nullable"
        timestamptz created_at "NOT NULL"
        timestamptz published_at "Nullable until confirmed"
        int attempts "NOT NULL"
    }
```

## Relationship and constraint notes

- `deliveries.courier_id` has a real foreign key to `couriers.id`; it is nullable to represent an unassigned delivery. Each delivery points to at most one current courier, and a courier can have zero or many currently assigned deliveries.
- `delivery_assignments.parcel_id` and `courier_id` have real foreign keys. A delivery can have multiple historical assignment rows, and a courier can appear in multiple assignments. `assigned_by` is a UUID without a foreign key.
- `delivery_proofs.parcel_id` is both its primary key and a foreign key to `deliveries.parcel_id`, so each proof belongs to exactly one delivery and each delivery has at most one proof.
- `proof_photos.parcel_id` is both its primary key and a foreign key to `delivery_proofs.parcel_id`. A proof has zero or one photo row; each photo belongs to exactly one proof.
- `processed_events.event_id` and `outbox_events.id` are standalone primary keys. Their event, aggregate, and correlation identifiers do not reference rows through database foreign keys.
- `deliveries.parcel_id` identifies a parcel owned by parcel-service, and `customer_id`, `merchant_id`, `couriers.id`, and `delivery_assignments.assigned_by` refer conceptually to identities managed by other services. Those cross-service references are not database foreign keys.
