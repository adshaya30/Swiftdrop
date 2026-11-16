package com.swiftdrop.parcel.create.dto;

import java.util.UUID;

/** Master 8.1: original or replayed HTTP response; replay controls Idempotent-Replay. */
public record CreationResult(int httpStatus, Object body, boolean replay, UUID parcelId) { }
