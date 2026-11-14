import { apiRequest } from './http';

/**
 * Merchant Webhook API client for Member #10.
 * Endpoints are mapped via API Gateway to notification-service.
 * SRS v2.1 – FR-WHK-001, FR-WHK-008, FR-WHK-009
 */

/**
 * Fetches merchant webhook configuration.
 * Returns { url, enabled, hasSecret } (never the secret).
 */
export async function getWebhook() {
  return apiRequest('GET', '/api/v1/merchant/webhook');
}

/**
 * Saves or updates merchant webhook configuration.
 * Data: { url, enabled }
 * The first save returns { url, enabled, hasSecret, secret }; later saves return { url, enabled, hasSecret }.
 */
export async function saveWebhook(data) {
  return apiRequest('PUT', '/api/v1/merchant/webhook', { body: data });
}

/**
 * Rotates the webhook secret.
 * Returns { secret } once with the newly generated whsec_ secret.
 */
export async function rotateSecret() {
  return apiRequest('POST', '/api/v1/merchant/webhook/rotate-secret');
}

/**
 * Enqueues an immediate test webhook ping (type: webhook.test).
 * Returns { message, eventId }
 */
export async function sendTest() {
  return apiRequest('POST', '/api/v1/merchant/webhook/test');
}

/**
 * Lists webhook delivery logs with optional status filtering and pagination.
 * Params: { status, page, size }
 */
export async function listDeliveries(params = {}) {
  const query = {};
  if (params.status) query.status = params.status;
  if (params.page !== undefined) query.page = params.page;
  if (params.size !== undefined) query.size = params.size;

  return apiRequest('GET', '/api/v1/merchant/webhook/deliveries', { query });
}

/**
 * Retries a FAILED webhook delivery.
 * ID: delivery UUID
 */
export async function retryDelivery(id) {
  return apiRequest('POST', `/api/v1/merchant/webhook/deliveries/${id}/retry`);
}

/**
 * Fetches webhook delivery summary statistics.
 * Returns { failed24h, pending, succeeded24h }
 * Reused by Member #6 merchant dashboard.
 */
export async function getWebhookSummary() {
  return apiRequest('GET', '/api/v1/merchant/webhook/summary');
}

export default {
  getWebhook,
  saveWebhook,
  rotateSecret,
  sendTest,
  listDeliveries,
  retryDelivery,
  getWebhookSummary,
};

