package com.swiftdrop.parcel.lifecycle.dto;

/** FR-PAR-013: GET /admin/dashboard/parcels, counted for today in Asia/Colombo. */
public record DashboardParcelsResponse(long deliveredToday, long failedToday) { }
