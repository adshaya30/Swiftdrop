package com.swiftdrop.parcel.create.service;

import com.swiftdrop.parcel.create.dto.CreationRequest;
import com.swiftdrop.parcel.create.dto.CreationResult;

/**
 * Master 8.1 public contract consumed by Member #6. Phase 1 defines no Spring bean
 * or persistence implementation. The implementation will perform idempotency lookup,
 * discount resolution, pricing and fee confirmation, then atomic parcel/key/outbox writes.
 */
public interface ParcelCreationService {
    CreationResult create(CreationRequest request);
}
