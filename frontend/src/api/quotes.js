import { apiRequest } from './http';

// FR-PRC-002/009: reference data and all fee values remain server-authoritative.
export function getDistricts() {
  return apiRequest('GET', '/api/v1/public/districts');
}

export function postQuote(body) {
  return apiRequest('POST', '/api/v1/public/quotes', { body });
}
