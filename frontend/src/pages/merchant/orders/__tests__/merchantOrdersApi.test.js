import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as http from '../../../../api/http';
import { listOrders, getOrder, cancelOrder, getDashboardSummary } from '../../../../api/merchantOrders';
import { downloadReceipt, receiptFileName } from '../../../../api/receipts';

vi.mock('../../../../api/http', () => ({
  apiRequest: vi.fn().mockResolvedValue({}),
  apiBlob: vi.fn().mockResolvedValue(new Blob(['%PDF'])),
}));

describe('TST-040 merchant order and receipt API clients (Member #6)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists orders with only the filters that are set (FR-PAR-008)', async () => {
    await listOrders({ status: 'CREATED', from: '2026-11-01', to: '', merchantReference: ' ORD-1 ', q: null,
      page: 0, size: 20, sort: 'createdAt,desc' });

    expect(http.apiRequest).toHaveBeenCalledWith('GET', '/api/v1/merchant/orders', {
      query: { status: 'CREATED', from: '2026-11-01', merchantReference: 'ORD-1', page: 0, size: 20,
        sort: 'createdAt,desc' },
    });
  });

  it('gets one order and the dashboard summary', async () => {
    await getOrder('abc-1');
    await getDashboardSummary();

    expect(http.apiRequest).toHaveBeenNthCalledWith(1, 'GET', '/api/v1/merchant/orders/abc-1');
    expect(http.apiRequest).toHaveBeenNthCalledWith(2, 'GET', '/api/v1/merchant/dashboard/summary');
  });

  it('cancels with a reason, or with no body when the reason is empty (BR-07)', async () => {
    await cancelOrder('abc-1', '  Customer changed mind  ');
    await cancelOrder('abc-2', '   ');

    expect(http.apiRequest).toHaveBeenNthCalledWith(1, 'POST', '/api/v1/merchant/orders/abc-1/cancel',
      { body: { reason: 'Customer changed mind' } });
    expect(http.apiRequest).toHaveBeenNthCalledWith(2, 'POST', '/api/v1/merchant/orders/abc-2/cancel', {});
  });

  it('downloads the receipt as a Blob through the shared client (FR-UI-011)', async () => {
    const blob = await downloadReceipt('p-1');

    expect(http.apiBlob).toHaveBeenCalledWith('/api/v1/parcels/p-1/receipt');
    expect(blob).toBeInstanceOf(Blob);
    expect(receiptFileName('SD-7K3M9QX2TA')).toBe('SwiftDrop-Receipt-SD-7K3M9QX2TA.pdf');
  });
});
