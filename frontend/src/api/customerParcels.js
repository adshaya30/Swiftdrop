import { apiRequest } from './http';

const parcelsPath = '/api/v1/customer/parcels';
const parcelPath = (id) => `${parcelsPath}/${encodeURIComponent(id)}`;

// FR-PAR-006: the confirmation screen owns the key; retries reuse it unchanged.
export function createParcel(body, idempotencyKey) {
  return apiRequest('POST', parcelsPath, { body, idempotencyKey });
}

export function listParcels(query = {}) {
  return apiRequest('GET', parcelsPath, { query });
}

export function getParcel(id) {
  return apiRequest('GET', parcelPath(id));
}

export function cancelParcel(id, body) {
  return apiRequest('POST', `${parcelPath(id)}/cancel`, { body });
}

// FR-PAR-015 (S): thin wrapper only; the endpoint may not be available yet.
export function getDashboardSummary() {
  return apiRequest('GET', '/api/v1/customer/dashboard/summary');
}
