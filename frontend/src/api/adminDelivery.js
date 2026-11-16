import { apiRequest } from './http';

const DELIVERY_FILTERS = [
  'assigned',
  'status',
  'courierId',
  'originDistrictCode',
  'destinationDistrictCode',
  'from',
  'to',
  'q',
  'page',
  'size',
  'sort',
];

function filteredParams(params, allowedKeys) {
  const query = {};
  for (const key of allowedKeys) {
    const value = params?.[key];
    if (value !== undefined && value !== null && value !== '') {
      query[key] = value;
    }
  }
  return query;
}

export function listDeliveries(params = {}) {
  return apiRequest('GET', '/api/v1/admin/deliveries', {
    query: filteredParams(params, DELIVERY_FILTERS),
  });
}

export function getDelivery(parcelId) {
  return apiRequest('GET', `/api/v1/admin/deliveries/${encodeURIComponent(parcelId)}`);
}

export function assignDelivery(parcelId, courierId) {
  return apiRequest('POST', `/api/v1/admin/deliveries/${encodeURIComponent(parcelId)}/assign`, {
    body: { courierId },
  });
}

export function listCouriers() {
  return apiRequest('GET', '/api/v1/admin/couriers');
}

export function listCourierDeliveries(courierId, params = {}) {
  return apiRequest('GET', `/api/v1/admin/couriers/${encodeURIComponent(courierId)}/deliveries`, {
    query: filteredParams(params, ['scope', 'page', 'size']),
  });
}

export function getDispatchDashboard() {
  return apiRequest('GET', '/api/v1/admin/dashboard/dispatch');
}
