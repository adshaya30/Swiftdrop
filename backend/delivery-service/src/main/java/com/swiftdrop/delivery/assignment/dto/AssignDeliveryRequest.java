package com.swiftdrop.delivery.assignment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignDeliveryRequest(@NotNull UUID courierId) {
}