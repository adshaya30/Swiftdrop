import { apiRequest } from './http';

/**
 * Admin audit-log API (Member #5). SRS 7.3, FR-TRK-005.
 * Filters: action, actorId, from, to (yyyy-MM-dd, Asia/Colombo, inclusive), page, size. Newest first.
 * Entry: { id, service, actorUserId, actorRole, action, entityType, entityId, details, occurredAt }.
 */
export function listAuditLogs(params = {}) {
  const query = {};
  for (const key of ['action', 'actorId', 'from', 'to', 'page', 'size']) {
    if (params[key] !== undefined && params[key] !== null && params[key] !== '') {
      query[key] = params[key];
    }
  }
  return apiRequest('GET', '/api/v1/admin/audit-logs', { query });
}
