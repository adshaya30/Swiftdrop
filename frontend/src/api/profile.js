import { apiRequest } from './http';

/**
 * Profile API module for Member #2.
 * SRS v2.1 – FR-AUTH-012, FR-AUTH-013, FR-AUTH-014
 */

export async function getMe() {
  return apiRequest('GET', '/api/v1/me');
}

export async function updateMe(data) {
  return apiRequest('PUT', '/api/v1/me', { body: data });
}

export async function changePassword({ currentPassword, newPassword }) {
  return apiRequest('POST', '/api/v1/me/change-password', {
    body: { currentPassword, newPassword },
  });
}

