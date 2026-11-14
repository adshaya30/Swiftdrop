import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, within, fireEvent, waitFor, act, renderHook } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as orders from '../../../../api/merchantOrders';
import * as adapter from '../../components/trackingAdapter';
import MerchantOrderDetailPage from '../index.jsx';

vi.setConfig({ testTimeout: 20000 });
vi.mock('../../../../api/merchantOrders');
vi.mock('../../../../api/receipts', () => ({
  downloadReceipt: vi.fn(),
  receiptFileName: (tn) => `SwiftDrop-Receipt-${tn}.pdf`,
}));
vi.mock('../../../courier/components/ProofViewer', () => ({
  default: ({ parcelId }) => <div data-testid="proof">proof for {parcelId}</div>,
}));

let statusListener = null;
vi.mock('../../components/trackingAdapter', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    useOnStatusChange: actual.useOnStatusChange,
    getTimeline: vi.fn(),
    ParcelTimeline: ({ events }) => <ol aria-label="Timeline">{(events ?? []).map((e) => (
      <li key={e.sequence}>{e.toStatus}</li>))}</ol>,
    hasTrackingStream: true,
    useStatusChanges: (trackingNumber, onChange) => { statusListener = onChange; },
  };
});

const ORDER = {
  id: 'o-1', trackingNumber: 'SD-7K3M9QX2TA', status: 'CREATED', ownerType: 'MERCHANT',
  merchantReference: 'ORD-1001',
  pickup: { contactName: 'Store Sender', contactPhone: '+94771234567', addressLine1: '12 Galle Road',
    city: 'Colombo 03', districtName: 'Colombo' },
  dropoff: { contactName: 'Kamala Silva', contactPhone: '+94712345678', addressLine1: '45 Peradeniya Road',
    city: 'Kandy', districtName: 'Kandy', contactEmail: 'kamala@example.com' },
  parcel: { weightKg: 2.0, size: 'MEDIUM', description: 'Books' },
  deliveryInstructions: 'Call on arrival',
  fee: { currency: 'LKR', baseFee: 500, weightFee: 150, sizeSurcharge: 150, discountAmount: 80, total: 720 },
  createdAt: '2026-11-13T04:30:00Z', updatedAt: '2026-11-13T04:30:00Z',
};
const TIMELINE = { parcelId: 'o-1', history: [{ sequence: 1, toStatus: 'CREATED' }] };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={['/merchant/orders/o-1']}>
          <Routes><Route path="/merchant/orders/:id" element={<MerchantOrderDetailPage />} /></Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 merchant order detail (Member #6, FR-PAR-008, FR-UI-007, SRS 7.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    statusListener = null;
    orders.getOrder.mockResolvedValue(ORDER);
    adapter.getTimeline.mockResolvedValue(TIMELINE);
  });

  it('shows the order detail, fee breakdown and timeline', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'SD-7K3M9QX2TA' })).toBeInTheDocument();
    expect(screen.getByText('Reference ORD-1001')).toBeInTheDocument();
    const dropoff = within(screen.getByRole('region', { name: 'Drop-off' }));
    expect(dropoff.getByText('Kamala Silva')).toBeInTheDocument();
    expect(dropoff.getByText(/45 Peradeniya Road, Kandy/)).toBeInTheDocument();
    const fee = within(screen.getByRole('region', { name: 'Fee' }));
    expect(fee.getByText('LKR 720.00')).toBeInTheDocument();
    expect(fee.getByText('LKR 80.00')).toBeInTheDocument();
    expect(await screen.findByRole('list', { name: 'Timeline' })).toHaveTextContent('CREATED');
    expect(adapter.getTimeline).toHaveBeenCalledWith('o-1');
    expect(screen.getByRole('button', { name: /download receipt/i })).toBeInTheDocument();
  });

  it('refetches the detail and timeline on every status event (FR-UI-007)', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'SD-7K3M9QX2TA' });
    expect(orders.getOrder).toHaveBeenCalledTimes(1);

    orders.getOrder.mockResolvedValue({ ...ORDER, status: 'PICKED_UP', updatedAt: '2026-11-13T05:00:00Z' });
    await act(async () => { statusListener(); });

    await waitFor(() => expect(orders.getOrder).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(adapter.getTimeline).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Picked Up')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancel order' })).toBeNull();
  });

  it('shows the proof of delivery only when DELIVERED', async () => {
    const { unmount } = renderPage();
    await screen.findByRole('heading', { name: 'SD-7K3M9QX2TA' });
    expect(screen.queryByTestId('proof')).toBeNull();
    unmount();

    orders.getOrder.mockResolvedValue({ ...ORDER, status: 'DELIVERED' });
    renderPage();
    expect(await screen.findByTestId('proof')).toHaveTextContent('proof for o-1');
  });

  it('shows an "updating" timeline while it is not projected yet (404 right after creation)', async () => {
    adapter.getTimeline.mockReturnValue(new Promise(() => {}));
    renderPage();

    expect(await screen.findByText('Timeline updating…')).toBeInTheDocument();
  });

  it('cancels a CREATED order after confirmation and refreshes', async () => {
    orders.cancelOrder.mockResolvedValue({ id: 'o-1', trackingNumber: 'SD-7K3M9QX2TA', status: 'CANCELLED' });
    renderPage();
    await screen.findByRole('heading', { name: 'SD-7K3M9QX2TA' });

    fireEvent.click(screen.getByRole('button', { name: 'Cancel order' }));
    orders.getOrder.mockResolvedValue({ ...ORDER, status: 'CANCELLED' });
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByText('The order was cancelled.')).toBeInTheDocument();
    expect(orders.cancelOrder).toHaveBeenCalledWith('o-1', '');
    await waitFor(() => expect(orders.getOrder).toHaveBeenCalledTimes(2));
  });

  it('explains INVALID_STATUS_TRANSITION when the order was picked up meanwhile', async () => {
    orders.cancelOrder.mockRejectedValue({ status: 409, code: 'INVALID_STATUS_TRANSITION' });
    renderPage();
    await screen.findByRole('heading', { name: 'SD-7K3M9QX2TA' });

    fireEvent.click(screen.getByRole('button', { name: 'Cancel order' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByText(/can no longer be cancelled/i)).toBeInTheDocument();
  });

  it('shows "Order not found" for an unknown or foreign order (404)', async () => {
    orders.getOrder.mockRejectedValue({ status: 404, code: 'NOT_FOUND' });
    renderPage();

    expect(await screen.findByText('Order not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to orders' })).toHaveAttribute('href', '/merchant/orders');
  });
});

describe('TST-040 status-change detection of the tracking adapter (FR-UI-007)', () => {
  it('calls back on a new status or update time, not on the first value or a repeat', () => {
    const onChange = vi.fn();
    const { rerender } = renderHook(({ view }) => adapter.useOnStatusChange(view, onChange),
      { initialProps: { view: undefined } });

    rerender({ view: { status: 'CREATED', updatedAt: 't1' } });
    rerender({ view: { status: 'CREATED', updatedAt: 't1' } });
    expect(onChange).not.toHaveBeenCalled();

    rerender({ view: { status: 'PICKED_UP', updatedAt: 't2' } });
    rerender({ view: { status: 'PICKED_UP', updatedAt: 't3' } });
    expect(onChange).toHaveBeenCalledTimes(2);
  });
});
