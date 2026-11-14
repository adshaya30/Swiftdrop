import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, within, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as orders from '../../../../api/merchantOrders';
import MerchantOrdersPage, { validateFilters } from '../index.jsx';

vi.setConfig({ testTimeout: 20000 });
vi.mock('../../../../api/merchantOrders');

const order = (id, status, extra = {}) => ({
  id, trackingNumber: `SD-${id.toUpperCase().padEnd(10, 'X')}`, status, merchantReference: `ORD-${id}`,
  dropoff: { city: 'Kandy' }, fee: { total: 1620.00 }, createdAt: '2026-11-13T04:30:00Z', ...extra,
});

const PAGE = { items: [order('a1', 'CREATED'), order('b2', 'DELIVERED')], page: 0, size: 20, totalItems: 2,
  totalPages: 1 };

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter><MerchantOrdersPage /></MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

const lastListCall = () => orders.listOrders.mock.calls.at(-1)[0];

describe('TST-040 merchant orders list (Member #6, FR-PAR-008)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    orders.listOrders.mockResolvedValue(PAGE);
  });

  it('lists orders with status chips, fee and links to the detail page', async () => {
    renderPage();

    const table = within(await screen.findByRole('table', { name: 'Orders' }));
    expect(table.getByRole('link', { name: PAGE.items[0].trackingNumber }))
      .toHaveAttribute('href', '/merchant/orders/a1');
    expect(table.getByText('ORD-a1')).toBeInTheDocument();
    expect(table.getByText('Delivered')).toBeInTheDocument();
    expect(table.getAllByText('LKR 1,620.00')).toHaveLength(2);
    expect(lastListCall()).toMatchObject({ page: 0, size: 20, sort: 'createdAt,desc' });
  });

  it('offers cancel only for CREATED orders', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    expect(screen.getByRole('button', { name: `Cancel order ${PAGE.items[0].trackingNumber}` })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: `Cancel order ${PAGE.items[1].trackingNumber}` })).toBeNull();
  });

  it('applies the filters (status, dates, reference, tracking prefix) and resets them', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.mouseDown(screen.getByLabelText('Status'));
    fireEvent.click(within(screen.getByRole('listbox')).getByText('Cancelled'));
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-11-13' } });
    fireEvent.change(screen.getByLabelText('Merchant reference'), { target: { value: 'ORD-1001' } });
    fireEvent.change(screen.getByLabelText('Tracking number starts with'), { target: { value: 'SD-7K' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));

    await waitFor(() => expect(lastListCall()).toMatchObject({
      status: 'CANCELLED', from: '2026-11-01', to: '2026-11-13', merchantReference: 'ORD-1001', q: 'SD-7K', page: 0,
    }));

    fireEvent.click(screen.getByRole('button', { name: 'Reset' }));
    await waitFor(() => expect(lastListCall()).toMatchObject({ status: '', from: '', q: '' }));
  });

  it('changes the sort order', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.mouseDown(screen.getByLabelText('Sort by'));
    fireEvent.click(within(screen.getByRole('listbox')).getByText('Oldest first'));

    await waitFor(() => expect(lastListCall()).toMatchObject({ sort: 'createdAt,asc' }));
  });

  it('does not send an invalid date range', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });
    const calls = orders.listOrders.mock.calls.length;

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-11-13' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-11-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));

    expect(await screen.findByText('Must be on or after the From date')).toBeInTheDocument();
    expect(orders.listOrders.mock.calls.length).toBe(calls);
    expect(validateFilters({ status: '', from: '', to: '', merchantReference: 'x'.repeat(65), q: 'x'.repeat(14) }))
      .toEqual({ merchantReference: 'At most 64 characters', q: 'At most 13 characters' });
  });

  it('pages through results', async () => {
    orders.listOrders.mockResolvedValue({ ...PAGE, totalItems: 45, totalPages: 3 });
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.click(screen.getByRole('button', { name: /next page/i }));

    await waitFor(() => expect(lastListCall()).toMatchObject({ page: 1, size: 20 }));
  });

  it('cancels after confirmation with an optional reason, then reloads', async () => {
    orders.cancelOrder.mockResolvedValue({ id: 'a1', trackingNumber: PAGE.items[0].trackingNumber,
      status: 'CANCELLED', statusChangedAt: '2026-11-14T05:00:00Z' });
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.click(screen.getByRole('button', { name: `Cancel order ${PAGE.items[0].trackingNumber}` }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(orders.cancelOrder).not.toHaveBeenCalled();
    fireEvent.change(dialog.getByLabelText('Reason (optional)'), { target: { value: 'Customer changed mind' } });
    fireEvent.click(dialog.getByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByText(`Order ${PAGE.items[0].trackingNumber} was cancelled.`)).toBeInTheDocument();
    expect(orders.cancelOrder).toHaveBeenCalledWith('a1', 'Customer changed mind');
    await waitFor(() => expect(orders.listOrders.mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  it('keeps the order when the dialog is dismissed', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.click(screen.getByRole('button', { name: `Cancel order ${PAGE.items[0].trackingNumber}` }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Keep order' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(orders.cancelOrder).not.toHaveBeenCalled();
  });

  it('shows MERCHANT_NOT_ACTIVE inside the dialog', async () => {
    orders.cancelOrder.mockRejectedValue({ status: 403, code: 'MERCHANT_NOT_ACTIVE' });
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.click(screen.getByRole('button', { name: `Cancel order ${PAGE.items[0].trackingNumber}` }));
    const dialog = within(await screen.findByRole('dialog'));
    fireEvent.click(dialog.getByRole('button', { name: 'Cancel order' }));

    expect(await dialog.findByRole('alert')).toHaveTextContent(/merchant account is not active/i);
  });

  it('explains INVALID_STATUS_TRANSITION and reloads the list', async () => {
    orders.cancelOrder.mockRejectedValue({ status: 409, code: 'INVALID_STATUS_TRANSITION' });
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });
    const calls = orders.listOrders.mock.calls.length;

    fireEvent.click(screen.getByRole('button', { name: `Cancel order ${PAGE.items[0].trackingNumber}` }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel order' }));

    expect(await screen.findByText(/can no longer be cancelled/i)).toBeInTheDocument();
    await waitFor(() => expect(orders.listOrders.mock.calls.length).toBeGreaterThan(calls));
  });

  it('blocks a reason longer than 300 characters', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    fireEvent.click(screen.getByRole('button', { name: `Cancel order ${PAGE.items[0].trackingNumber}` }));
    const dialog = within(await screen.findByRole('dialog'));
    fireEvent.change(dialog.getByLabelText('Reason (optional)'), { target: { value: 'x'.repeat(301) } });

    expect(dialog.getByRole('button', { name: 'Cancel order' })).toBeDisabled();
    expect(dialog.getByText('301/300')).toBeInTheDocument();
  });

  it('shows empty and error states', async () => {
    orders.listOrders.mockResolvedValueOnce({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    const { unmount } = renderPage();
    expect(await screen.findByText('No orders found')).toBeInTheDocument();
    unmount();

    orders.listOrders.mockRejectedValueOnce({ status: 503 });
    renderPage();
    expect(await screen.findByText('Orders unavailable')).toBeInTheDocument();
  });

  it('has no create-order button (orders are API only, D-23)', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Orders' });

    expect(screen.queryByRole('button', { name: /create|new order/i })).toBeNull();
  });
});
