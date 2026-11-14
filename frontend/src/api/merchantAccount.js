import { apiRequest } from './http';

/**
 * Merchant account API: registration and API credentials of the logged-in merchant.
 * SRS v2.1 - FR-AUTH-016 (register), FR-AUTH-018 (credentials). One function per endpoint (SRS 7.3).
 *
 * The client secret is returned only by createCredential, once. It is never listed again and must be kept in
 * component state only (never in browser storage).
 */

/** POST /auth/register-merchant (public). Resolves with { message }; no tokens are issued. */
export async function registerMerchant(data) {
  return apiRequest('POST', '/api/v1/auth/register-merchant', { body: data });
}

/** GET /merchant/credentials. Resolves with { items, page, size, totalItems, totalPages }. */
export async function listCredentials({ page, size } = {}) {
  const query = {};
  if (page !== undefined) query.page = page;
  if (size !== undefined) query.size = size;
  return apiRequest('GET', '/api/v1/merchant/credentials', { query });
}

/** POST /merchant/credentials { label }. Resolves with { id, clientId, clientSecret, label, status, createdAt }. */
export async function createCredential(label) {
  return apiRequest('POST', '/api/v1/merchant/credentials', { body: { label } });
}

/** DELETE /merchant/credentials/{id}. Resolves with null (204); revoking twice is harmless. */
export async function revokeCredential(id) {
  return apiRequest('DELETE', `/api/v1/merchant/credentials/${encodeURIComponent(id)}`);
}
