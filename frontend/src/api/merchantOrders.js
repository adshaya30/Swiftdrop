import { apiRequest } from './http';

/**
 * Merchant order API (Member #6, SRS 7.3 parcel-service, FR-PAR-008 and FR-PAR-014).
 * Orders are created through the merchant API only (D-23), so there is no createOrder here.
 */

const ORDERS = '/api/v1/merchant/orders';

/**
 * Lists the merchant's own orders.
 * Params: { status, from, to (yyyy-MM-dd, Asia/Colombo, inclusive), merchantReference, q (tracking-number prefix),
 * page (0-based), size (1-100), sort ("createdAt,desc" | "status,asc" | "trackingNumber,asc" ...) }.
 * Empty values are left out. Returns { items, page, size, totalItems, totalPages }.
 */
export async function listOrders(params = {}) {
  const query = {};
  for (const key of ['status', 'from', 'to', 'merchantReference', 'q', 'page', 'size', 'sort']) {
    const value = params[key];
    if (value !== undefined && value !== null && value !== '') {
      query[key] = typeof value === 'string' ? value.trim() : value;
    }
  }
  return apiRequest('GET', ORDERS, { query });
}

/** Order detail; another merchant's order answers 404. */
export async function getOrder(id) {
  return apiRequest('GET', `${ORDERS}/${encodeURIComponent(id)}`);
}

/**
 * Cancels an order while CREATED (BR-07). The reason is optional, at most 300 characters.
 * Returns { id, trackingNumber, status, statusChangedAt }.
 */
export async function cancelOrder(id, reason) {
  const trimmed = typeof reason === 'string' ? reason.trim() : '';
  return apiRequest('POST', `${ORDERS}/${encodeURIComponent(id)}/cancel`,
    trimmed ? { body: { reason: trimmed } } : {});
}

/** { activeCounts, last30DaysCounts, recentOrders[10] } (dashboard login only). */
export async function getDashboardSummary() {
  return apiRequest('GET', '/api/v1/merchant/dashboard/summary');
}

export default { listOrders, getOrder, cancelOrder, getDashboardSummary };
