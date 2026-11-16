import { apiRequest } from './http';

/**
 * Admin merchant administration API (ADMIN role). SRS v2.1 - FR-AUTH-017, FR-AUTH-024.
 * One function per endpoint (SRS 7.3). Status actions resolve with the updated merchant detail.
 */

/** GET /admin/merchants. Filters: status, q (business name prefix); paging: page, size; sort "field,dir". */
export async function listMerchants({ status, q, page, size, sort } = {}) {
  const query = {};
  if (status) query.status = status;
  if (q) query.q = q;
  if (page !== undefined) query.page = page;
  if (size !== undefined) query.size = size;
  if (sort) query.sort = sort;
  return apiRequest('GET', '/api/v1/admin/merchants', { query });
}

export async function getMerchant(id) {
  return apiRequest('GET', `/api/v1/admin/merchants/${encodeURIComponent(id)}`);
}

/** From PENDING or REJECTED. */
export async function approveMerchant(id) {
  return apiRequest('POST', `/api/v1/admin/merchants/${encodeURIComponent(id)}/approve`);
}

/** From PENDING; reason of 10-300 characters. */
export async function rejectMerchant(id, reason) {
  return apiRequest('POST', `/api/v1/admin/merchants/${encodeURIComponent(id)}/reject`, { body: { reason } });
}

/** From APPROVED; reason of 10-300 characters. */
export async function suspendMerchant(id, reason) {
  return apiRequest('POST', `/api/v1/admin/merchants/${encodeURIComponent(id)}/suspend`, { body: { reason } });
}

/** From SUSPENDED. */
export async function reinstateMerchant(id) {
  return apiRequest('POST', `/api/v1/admin/merchants/${encodeURIComponent(id)}/reinstate`);
}

/** PATCH .../discount: 0.00 to 50.00 with at most two decimals. */
export async function setDiscount(id, discountPercent) {
  return apiRequest('PATCH', `/api/v1/admin/merchants/${encodeURIComponent(id)}/discount`, {
    body: { discountPercent },
  });
}

/** GET /admin/dashboard/merchants (FR-AUTH-024). Resolves with the number of merchants waiting for approval. */
export async function pendingCount() {
  const result = await apiRequest('GET', '/api/v1/admin/dashboard/merchants');
  return Number(result?.pendingMerchants ?? 0);
}
