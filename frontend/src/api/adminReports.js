import { apiRequest } from './http';

/** Admin report API (Member #5). SRS 7.3, FR-PAR-013. from/to are yyyy-MM-dd in Asia/Colombo, inclusive. */

function rangeQuery({ from, to } = {}) {
  const query = {};
  if (from) query.from = from;
  if (to) query.to = to;
  return query;
}

/** Totals and rates for the range: created, delivered, failed, cancelled, inProgress, successRate, failureRate. */
export function getReportSummary(range) {
  return apiRequest('GET', '/api/v1/admin/reports/summary', { query: rangeQuery(range) });
}

/** One row per Asia/Colombo day: date, created, delivered, failed, cancelled. */
export function getDailyReport(range) {
  return apiRequest('GET', '/api/v1/admin/reports/daily', { query: rangeQuery(range) });
}

/** Dashboard parcel card: { deliveredToday, failedToday }. */
export function getDashboardParcels() {
  return apiRequest('GET', '/api/v1/admin/dashboard/parcels');
}
