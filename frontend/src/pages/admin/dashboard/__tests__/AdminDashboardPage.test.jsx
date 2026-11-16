import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as reportsApi from '../../../../api/adminReports';
import AdminDashboardPage, { DashboardView } from '../index.jsx';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../api/adminReports');

const FakeDispatch = () => <div>Unassigned parcels: 7</div>;
const FakeMerchants = () => <div>Pending merchants: 3</div>;
const Crashing = () => { throw new Error('boom'); };

function renderView(props) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <DashboardView {...props} />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 Member #5 admin dashboard (FR-PAR-013, SRS 7.6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    reportsApi.getDashboardParcels.mockResolvedValue({ deliveredToday: 12, failedToday: 2 });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows delivered and failed today next to the dispatch and pending-merchants cards', async () => {
    renderView({ DispatchCard: FakeDispatch, PendingMerchantsCard: FakeMerchants });
    const parcels = within(await screen.findByRole('region', { name: 'Parcels today' }));
    expect(await parcels.findByText('12')).toBeInTheDocument();
    expect(parcels.getByText('Delivered today')).toBeInTheDocument();
    expect(parcels.getByText('2')).toBeInTheDocument();
    expect(parcels.getByText('Failed today')).toBeInTheDocument();
    expect(screen.getByText('Unassigned parcels: 7')).toBeInTheDocument();
    expect(screen.getByText('Pending merchants: 3')).toBeInTheDocument();
  });

  it('a failing parcels card shows its own error and retry, and never blanks the other cards', async () => {
    reportsApi.getDashboardParcels.mockRejectedValueOnce({ detail: 'Parcel service is down' });
    renderView({ DispatchCard: FakeDispatch, PendingMerchantsCard: FakeMerchants });

    expect(await screen.findByText('Parcel service is down')).toBeInTheDocument();
    expect(screen.getByText('Unassigned parcels: 7')).toBeInTheDocument();
    expect(screen.getByText('Pending merchants: 3')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByText('12')).toBeInTheDocument();
  });

  it('a card that crashes is replaced by its own message while the other cards keep working', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    renderView({ DispatchCard: Crashing, PendingMerchantsCard: FakeMerchants });

    expect(await screen.findByText('This section could not be displayed.')).toBeInTheDocument();
    expect(await screen.findByText('12')).toBeInTheDocument();
    expect(screen.getByText('Pending merchants: 3')).toBeInTheDocument();
  });

  it('each card keeps its own loading state: a slow parcels card does not delay the others', async () => {
    reportsApi.getDashboardParcels.mockReturnValue(new Promise(() => {}));
    renderView({ DispatchCard: FakeDispatch, PendingMerchantsCard: FakeMerchants });
    expect(await screen.findByText('Loading...')).toBeInTheDocument();
    expect(screen.getByText('Unassigned parcels: 7')).toBeInTheDocument();
    expect(screen.getByText('Pending merchants: 3')).toBeInTheDocument();
  });

  it('shows placeholder tiles for sibling cards that are not merged yet', async () => {
    renderView({});
    expect(await screen.findByText('Dispatch not available yet')).toBeInTheDocument();
    expect(screen.getByText('Pending merchants not available yet')).toBeInTheDocument();
    expect(await screen.findByText('12')).toBeInTheDocument();
  });

  it('the page itself builds and renders with whatever sibling cards exist', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={client}>
        <ThemeProvider theme={theme}>
          <AdminDashboardPage />
        </ThemeProvider>
      </QueryClientProvider>,
    );
    expect(await screen.findByText('Delivered today')).toBeInTheDocument();
  });
});
