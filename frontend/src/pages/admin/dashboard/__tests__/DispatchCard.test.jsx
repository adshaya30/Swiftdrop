import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as deliveryApi from '../../../../api/adminDelivery';
import * as reportsApi from '../../../../api/adminReports';
import { DashboardView } from '../index.jsx';
import { DispatchCard } from '../../dispatch/DispatchCard.jsx';


vi.mock('../../../../api/adminDelivery');
vi.mock('../../../../api/adminReports');

const FakeMerchants = () => <div>Pending merchants: 3</div>;

function renderCard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <DispatchCard />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

function renderDashboard() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <DashboardView DispatchCard={DispatchCard} PendingMerchantsCard={FakeMerchants} />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 Member #7 dispatch dashboard card', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    deliveryApi.getDispatchDashboard.mockResolvedValue({ unassigned: 7, inProgress: 4, activeCouriers: 3 });
    reportsApi.getDashboardParcels.mockResolvedValue({ deliveredToday: 12, failedToday: 2 });
  });

  it('renders dispatch totals and links to the dispatch queue', async () => {
    renderCard();
    const card = await screen.findByRole('region', { name: 'Dispatch' });
    expect(await within(card).findByText('Unassigned')).toBeInTheDocument();
    expect(card).toHaveTextContent('7');
    expect(card).toHaveTextContent('In progress');
    expect(card).toHaveTextContent('4');
    expect(card).toHaveTextContent('Active couriers');
    expect(card).toHaveTextContent('3');
    expect(within(card).getByRole('link', { name: 'Open dispatch queue' })).toHaveAttribute(
      'href',
      '/admin/parcels/unassigned',
    );
  });

  it('shows an independent loading state while the dashboard request is pending', async () => {
    deliveryApi.getDispatchDashboard.mockReturnValueOnce(new Promise(() => {}));
    renderCard();
    expect(await screen.findByText('Loading...')).toBeInTheDocument();
  });

  it('shows an error and retries the dispatch totals request', async () => {
    deliveryApi.getDispatchDashboard.mockRejectedValueOnce({ detail: 'Dispatch totals unavailable' });
    renderCard();
    expect(await screen.findByText('Dispatch totals unavailable')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('Active couriers')).toBeInTheDocument();
  });

  it('keeps the other dashboard cards available when dispatch totals fail', async () => {
    deliveryApi.getDispatchDashboard.mockRejectedValueOnce({ detail: 'Dispatch totals unavailable' });
    renderDashboard();
    expect(await screen.findByText('Dispatch totals unavailable')).toBeInTheDocument();
    expect(screen.getByText('Delivered today')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('Pending merchants: 3')).toBeInTheDocument();
  });

  it('offers keyboard-operable queue navigation', async () => {
    renderCard();
    const link = await screen.findByRole('link', { name: 'Open dispatch queue' });
    link.focus();
    expect(link).toHaveFocus();
  });
});
