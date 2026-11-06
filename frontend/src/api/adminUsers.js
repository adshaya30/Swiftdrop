import { apiRequest } from './http';

/**
 * Admin User Management API module for Member #2.
 * SRS v2.1 – FR-AUTH-020, FR-AUTH-021
 */

export async function getUsers(params = {}) {
  const query = {};
  if (params.role) query.role = params.role;
  if (params.status) query.status = params.status;
  if (params.q) query.q = params.q;
  if (params.page !== undefined) query.page = params.page;
  if (params.size !== undefined) query.size = params.size;
  if (params.sort) query.sort = params.sort;

  return apiRequest('GET', '/api/v1/admin/users', { query });
}

export async function getUser(id) {
  return apiRequest('GET', `/api/v1/admin/users/${id}`);
}

export async function createUser(data) {
  return apiRequest('POST', '/api/v1/admin/users', { body: data });
}

export async function updateUser(id, data) {
  return apiRequest('PATCH', `/api/v1/admin/users/${id}`, { body: data });
}

export async function deactivateUser(id) {
  return apiRequest('POST', `/api/v1/admin/users/${id}/deactivate`);
}

export async function activateUser(id) {
  return apiRequest('POST', `/api/v1/admin/users/${id}/activate`);
}

export async function resetPassword(id) {
  return apiRequest('POST', `/api/v1/admin/users/${id}/reset-password`);
}
