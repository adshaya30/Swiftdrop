import { apiRequest, apiForm } from './http';

/**
 * Courier deliveries API client (SRS FR-DEL-003, FR-DEL-004, FR-DEL-005).
 */

/**
 * List deliveries assigned to the calling courier.
 * @param {string} [scope='today'] - 'today' | 'active' | 'completed'
 * @param {number} [page=0] - 0-based page number
 * @param {number} [size=20] - page size
 */
export async function listDeliveries(scope = 'today', page = 0, size = 20) {
  const query = {};
  if (scope) query.scope = scope;
  if (page !== undefined) query.page = page;
  if (size !== undefined) query.size = size;

  return apiRequest('GET', '/api/v1/courier/deliveries', { query });
}

/**
 * Get detailed parcel info for an assigned delivery (without fees).
 * @param {string} parcelId - UUID of the parcel
 */
export async function getDelivery(parcelId) {
  return apiRequest('GET', `/api/v1/courier/deliveries/${parcelId}`);
}

/**
 * Update delivery status with multipart form data (Flow 3).
 * @param {string} parcelId - UUID of the parcel
 * @param {FormData} formData - multipart form containing status, note?, recipientName?, failureReason?, photo?
 */
export async function updateStatus(parcelId, formData) {
  return apiForm(`/api/v1/courier/deliveries/${parcelId}/status`, formData);
}

