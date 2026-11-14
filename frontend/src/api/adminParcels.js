import { apiRequest } from './http';

/**
 * Admin parcel API (Member #5). SRS 7.3, FR-PAR-011, FR-PAR-012.
 * Lists answer { items, page, size, totalItems, totalPages }.
 */

/** FR-PAR-011: filters status, ownerType, from, to (yyyy-MM-dd, Asia/Colombo), q, district codes. */
export function listAdminParcels(params = {}) {
  const query = {};
  for (const key of ['status', 'ownerType', 'from', 'to', 'q', 'originDistrictCode', 'destinationDistrictCode',
    'page', 'size', 'sort']) {
    if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
      query[key] = params[key];
    }
  }
  return apiRequest('GET', '/api/v1/admin/parcels', { query });
}

/** FR-PAR-011: full detail incl. contacts, addresses, fee, owner ids and merchant reference. */
export function getAdminParcel(id) {
  return apiRequest('GET', `/api/v1/admin/parcels/${encodeURIComponent(id)}`);
}

/** FR-PAR-011, BR-07: cancel a CREATED parcel; reason 10-300 characters. */
export function cancelAdminParcel(id, reason) {
  return apiRequest('POST', `/api/v1/admin/parcels/${encodeURIComponent(id)}/cancel`, { body: { reason } });
}

/** FR-PAR-012: force-fail from PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY; failureReason PARCEL_DAMAGED or OTHER. */
export function failAdminParcel(id, { failureReason, note }) {
  return apiRequest('POST', `/api/v1/admin/parcels/${encodeURIComponent(id)}/fail`, {
    body: { failureReason, note },
  });
}
