import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, within, act, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as orders from '../../../../api/merchantOrders';
import * as webhook from '../../../../api/webhook';
import MerchantDashboardPage, { REFRESH_MS } from '../index.jsx';

vi.setConfig({ testTimeout: 20000 });
vi.mock('../../../../api/merchantOrders');
vi.mock('../../../../api/webhook');

const SUMMARY = {
  activeCounts: { CREATED: 4, PICKED_UP: 2, IN_TRANSIT: 1, OUT_FOR_DELIVERY: 0 },
  last30DaysCounts: { CREATED: 4, PICKED_UP: 2, IN_TRANSIT: 1, OUT_FOR_DELIVERY: 0, DELIVERED: 9, FAILED: 1,
    CANCELLED: 3 },
  recentOrders: [
    { id: 'o-1', trackingNumber: 'SD-7K3M9QX2TA', status: 'CREATED', merchantReference: 'ORD-1001',
      dropoffCity: 'Kandy', createdAt: '2026-11-13T04:30:00Z' },
    { id: 'o-2', trackingNumber: 'SD-9ABCDEFGHJ', status: 'DELIVERED', merchantReference: null,
      dropoffCity: 'Jaffna', createdAt: '2026-11-12T04:30:00Z' },
  ],
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter><MerchantDashboardPage /></MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 merchant dashboard (Member #6, FR-PAR-014, FR-UI-008)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    orders.getDashboardSummary.mockResolvedValue(SUMMARY);
    webhook.getWebhookSummary.mockResolvedValue({ failed24h: 2, pending: 5, succeeded24h: 40 });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows active and last-30-day counts per status', async () => {
    renderPage();

    const active = within(await screen.findByRole('region', { name: 'Active orders' }));
    expect(active.getByTestId('active-orders-CREATED')).toHaveTextContent('4');
    expect(active.getByTestId('active-orders-OUT_FOR_DELIVERY')).toHaveTextContent('0');
    expect(active.queryByTestId('active-orders-DELIVERED')).toBeNull();
    const month = within(screen.getByRole('region', { name: 'Orders in the last 30 days' }));
    expect(month.getByTestId('last-30-days-DELIVERED')).toHaveTextContent('9');
    expect(month.getByTestId('last-30-days-CANCELLED')).toHaveTextContent('3');
  });

  it('lists recent orders with links to their detail pages and status chips', async () => {
    renderPage();

    const recent = within(await screen.findByRole('region', { name: 'Recent orders' }));
    expect(recent.getByRole('link', { name: 'SD-7K3M9QX2TA' })).toHaveAttribute('href', '/merchant/orders/o-1');
    expect(recent.getByText(/ORD-1001 · Kandy/)).toBeInTheDocument();
    expect(recent.getByText('Delivered')).toBeInTheDocument();
  });

  it('shows the webhook delivery counters', async () => {
    renderPage();

    const health = within(await screen.findByRole('region', { name: 'Webhook deliveries' }));
    expect(await health.findByText('40')).toBeInTheDocument();
    expect(health.getByText('2')).toBeInTheDocument();
    expect(health.getByText('5')).toBeInTheDocument();
  });

  it('shows "unavailable" for the webhook card without breaking the rest of the page', async () => {
    webhook.getWebhookSummary.mockRejectedValue({ status: 503, code: 'SERVICE_UNAVAILABLE' });
    renderPage();

    const health = within(await screen.findByRole('region', { name: 'Webhook deliveries' }));
    expect(await health.findByText(/unavailable/i)).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Active orders' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Recent orders' })).toBeInTheDocument();
  });

  it('says that orders are created through the API and links to the credentials page (D-23)', async () => {
    renderPage();

    expect(await screen.findByText(/created by your store through the SwiftDrop merchant API/i))
      .toBeInTheDocument();
    expect(screen.getByRole('link', { name: /manage your api credentials/i }))
      .toHaveAttribute('href', '/merchant/credentials');
    expect(screen.queryByRole('button', { name: /create order/i })).toBeNull();
  });

  it('refreshes the summary and the webhook card every 30 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderPage();
    await screen.findByRole('region', { name: 'Active orders' });
    expect(orders.getDashboardSummary).toHaveBeenCalledTimes(1);

    await act(async () => { vi.advanceTimersByTime(REFRESH_MS); });

    expect(orders.getDashboardSummary).toHaveBeenCalledTimes(2);
    expect(webhook.getWebhookSummary).toHaveBeenCalledTimes(2);
    expect(REFRESH_MS).toBe(30000);
  });

  it('shows an empty state when there are no orders', async () => {
    orders.getDashboardSummary.mockResolvedValue({ ...SUMMARY, recentOrders: [] });
    renderPage();

    expect(await screen.findByText('No orders yet')).toBeInTheDocument();
  });

  it('shows an error with retry when the summary fails, and the webhook card still renders', async () => {
    orders.getDashboardSummary.mockRejectedValueOnce({ status: 503 });
    renderPage();

    expect(await screen.findByText('Dashboard unavailable')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Webhook deliveries' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByRole('region', { name: 'Active orders' })).toBeInTheDocument();
  });

  it('uses headings and labelled regions for screen readers (WCAG 2.1 AA basics)', async () => {
    renderPage();

    await screen.findByRole('region', { name: 'Active orders' });
    expect(screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)).toEqual(
      expect.arrayContaining(['Active orders', 'Orders in the last 30 days', 'Webhook deliveries', 'Recent orders']),
    );
  });
});
