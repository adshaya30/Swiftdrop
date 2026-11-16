import { apiRequest } from './http';

/**
 * Admin zones, pricing and size-surcharge API (Member #5). SRS 7.3, FR-PRC-006, FR-PRC-007.
 * Money values are decimals as returned by the server; the client never calculates fees.
 */

/** Zones with their districts: [{ id, code, name, active, districts: [{ code, name }] }]. */
export function listZones() {
  return apiRequest('GET', '/api/v1/admin/zones');
}

/** code matches [A-Z_]{2,20} and is immutable; a duplicate code or name answers 400 VALIDATION_ERROR. */
export function createZone({ code, name }) {
  return apiRequest('POST', '/api/v1/admin/zones', { body: { code, name } });
}

/** Rename and/or (de)activate; deactivating a zone that still has districts answers 409 ZONE_IN_USE. */
export function updateZone(id, body) {
  return apiRequest('PATCH', `/api/v1/admin/zones/${encodeURIComponent(id)}`, { body });
}

/** A district belongs to exactly one zone. */
export function moveDistrict(districtCode, zoneId) {
  return apiRequest('PUT', `/api/v1/admin/districts/${encodeURIComponent(districtCode)}/zone`, {
    body: { zoneId },
  });
}

/** Full matrix: [{ originZoneId, originZoneCode, destinationZoneId, destinationZoneCode, baseFee, perKgFee }]. */
export function getPricingMatrix() {
  return apiRequest('GET', '/api/v1/admin/pricing');
}

/** Creates the directed pair if missing. Fees are >= 0 with 2 decimals, at most 1000000.00. */
export function setPricing(originZoneId, destinationZoneId, { baseFee, perKgFee }) {
  return apiRequest('PUT',
    `/api/v1/admin/pricing/${encodeURIComponent(originZoneId)}/${encodeURIComponent(destinationZoneId)}`,
    { body: { baseFee, perKgFee } });
}

/** [{ size, surcharge, description }] for SMALL, MEDIUM and LARGE. */
export function getSizeSurcharges() {
  return apiRequest('GET', '/api/v1/admin/size-surcharges');
}

/** Body is a list of { size, surcharge } holding SMALL, MEDIUM and LARGE once each. */
export function setSizeSurcharges(items) {
  return apiRequest('PUT', '/api/v1/admin/size-surcharges', { body: items });
}
