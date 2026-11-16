/**
 * TanStack Query keys of the merchant pages. They share the prefix ['admin', 'merchants'], so one invalidation after a
 * status action refreshes the directory, the details and the pending counter of the dashboard card together.
 */
export const MERCHANTS_QUERY_KEY = ['admin', 'merchants'];
export const PENDING_COUNT_QUERY_KEY = [...MERCHANTS_QUERY_KEY, 'pending-count'];
export const merchantDetailKey = (id) => [...MERCHANTS_QUERY_KEY, 'detail', id];
