import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, setAccessToken } from '../../../api/http';
import { getDistricts, postQuote } from '../../../api/quotes';
import { createParcel, listParcels, getParcel, cancelParcel, getDashboardSummary } from '../../../api/customerParcels';

describe('TST-040 customer API wire contracts through the shared client', () => {
  let fetchMock;
  beforeEach(() => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    clearAccessToken();
  });
  afterEach(() => {
    clearAccessToken();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });
  const respond = (data, status = 200) => new Response(JSON.stringify(data), {
    status, headers: { 'Content-Type': 'application/json' },
  });

  it('returns districts, dynamic maximum and sizes without rewriting them', async () => {
    const data = { maxWeightKg: '17.25', sizes: [{ size: 'SMALL', surcharge: '0.00', description: 'Small' }], districts: [{ code: 'COLOMBO' }] };
    fetchMock.mockResolvedValue(respond(data));
    expect(await getDistricts()).toEqual(data);
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/public/districts', expect.objectContaining({ method: 'GET' }));
  });

  it('posts quote inputs and preserves the complete server fee response', async () => {
    const body = { originDistrictCode: 'COLOMBO', destinationDistrictCode: 'KANDY', weightKg: '2.00', size: 'MEDIUM' };
    const fee = { currency: 'LKR', originZone: { code: 'WESTERN' }, destinationZone: { code: 'CENTRAL' }, baseFee: '500.00', weightFee: '150.00', sizeSurcharge: '150.00', discountAmount: '0.00', total: '800.00' };
    fetchMock.mockResolvedValue(respond(fee));
    expect(await postQuote(body)).toEqual(fee);
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/public/quotes', expect.objectContaining({ method: 'POST', body: JSON.stringify(body) }));
  });

  it('sends the supplied idempotency key, bearer token and exact expectedFee on creation and retry', async () => {
    setAccessToken('customer-token');
    const body = { pickup: {}, dropoff: {}, parcel: { weightKg: '1.20', size: 'SMALL' }, expectedFee: '650.00' };
    const result = { id: 'parcel-id', trackingNumber: 'SD-0123456789', status: 'CREATED', fee: { total: '650.00', currency: 'LKR' }, createdAt: '2026-11-14T00:00:00Z' };
    fetchMock.mockImplementation(() => Promise.resolve(respond(result, 201)));
    expect(await createParcel(body, 'confirmation-key')).toEqual(result);
    await createParcel(body, 'confirmation-key');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    for (const [url, options] of fetchMock.mock.calls) {
      expect(url).toBe('/api/v1/customer/parcels');
      expect(options).toMatchObject({ method: 'POST', credentials: 'include', body: JSON.stringify(body), headers: { 'Idempotency-Key': 'confirmation-key', Authorization: 'Bearer customer-token' } });
    }
  });

  it('keeps an optional idempotency key absent when not supplied', async () => {
    fetchMock.mockResolvedValue(respond({}, 201));
    await createParcel({});
    expect(fetchMock.mock.calls[0][1].headers).not.toHaveProperty('Idempotency-Key');
  });

  it('passes all list filters, inclusive date strings, zero-based page and sort', async () => {
    const query = { status: 'CREATED', from: '2026-11-01', to: '2026-11-14', q: 'SD-01', page: 0, size: 5, sort: 'trackingNumber,asc' };
    const page = { items: [{ id: 'one' }], page: 0, size: 5, totalItems: 1, totalPages: 1 };
    fetchMock.mockResolvedValue(respond(page));
    expect(await listParcels(query)).toEqual(page);
    const url = new URL(fetchMock.mock.calls[0][0], 'http://localhost');
    expect(url.pathname).toBe('/api/v1/customer/parcels');
    expect(Object.fromEntries(url.searchParams)).toEqual(Object.fromEntries(Object.entries(query).map(([key, value]) => [key, String(value)])));
  });

  it('leaves default list parameters to the backend', async () => {
    fetchMock.mockResolvedValue(respond({ items: [] }));
    await listParcels();
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/customer/parcels');
  });

  it('gets detail and posts optional cancellation reasons with encoded ids', async () => {
    const detail = { id: 'one', pickup: { city: 'Colombo' }, fee: { total: '500.00' } };
    fetchMock.mockResolvedValueOnce(respond(detail)).mockResolvedValueOnce(respond({ status: 'CANCELLED' })).mockResolvedValueOnce(respond({ status: 'CANCELLED' }));
    expect(await getParcel('one/two')).toEqual(detail);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/customer/parcels/one%2Ftwo');
    expect(await cancelParcel('one', { reason: 'Plans changed' })).toEqual({ status: 'CANCELLED' });
    expect(fetchMock.mock.calls[1]).toEqual(['/api/v1/customer/parcels/one/cancel', expect.objectContaining({ method: 'POST', body: '{"reason":"Plans changed"}' })]);
    await cancelParcel('one');
    expect(fetchMock.mock.calls[2][1]).not.toHaveProperty('body');
  });

  it('requests the summary without adding fallback behavior', async () => {
    fetchMock.mockResolvedValue(respond({ active: 2, delivered: 1 }));
    expect(await getDashboardSummary()).toEqual({ active: 2, delivered: 1 });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/customer/dashboard/summary');
  });

  it.each([
    [400, 'VALIDATION_ERROR'], [404, 'NOT_FOUND'], [409, 'FEE_CHANGED'],
    [409, 'INVALID_STATUS_TRANSITION'], [422, 'IDEMPOTENCY_KEY_REUSED'],
    [422, 'ZONE_NOT_SERVICEABLE'], [422, 'PRICING_NOT_CONFIGURED'], [503, 'SERVICE_UNAVAILABLE'],
  ])('preserves shared-client problem status %s and code %s', async (status, code) => {
    const errors = [{ field: 'parcel.weightKg', message: 'Invalid weight' }];
    fetchMock.mockResolvedValue(respond({ code, detail: 'Request rejected', errors }, status));
    await expect(createParcel({}, 'same-key')).rejects.toMatchObject({ status, code, detail: 'Request rejected', errors });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
