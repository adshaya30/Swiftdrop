package com.swiftdrop.delivery.assignment.controller;

import com.swiftdrop.delivery.assignment.dto.ActiveCountResponse;
import com.swiftdrop.delivery.assignment.service.DeliveryAdminService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/couriers")
public class InternalCourierController {

    private final DeliveryAdminService service;

    public InternalCourierController(DeliveryAdminService service) {
        this.service = service;
    }

    @GetMapping("/{courierId}/active-count")
    public ActiveCountResponse activeCount(@PathVariable UUID courierId) {
        return new ActiveCountResponse(service.activeCount(courierId));
    }
}