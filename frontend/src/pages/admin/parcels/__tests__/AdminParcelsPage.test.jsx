import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as parcelsApi from '../../../../api/adminParcels';
import * as pricingApi from '../../../../api/adminPricing';
import AdminParcelsPage from '../index.jsx';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../api/adminParcels');
vi.mock('../../../../api/adminPricing');

const parcel = (over = {}) => ({
  id: 'p-1',
  trackingNumber: 'SD-20260101-ABCD',
  status: 'IN_TRANSIT',
  ownerType: 'MERCHANT',
  merchantReference: 'ORD-77',
  pickup: { districtName: 'Colombo' },
  dropoff: { districtName: 'Kandy' },
  fee: { total: '800.00' },
  createdAt: '2026-03-01T18:45:00Z',
  ...over,
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <AdminParcelsPage />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

const lastCall = () => parcelsApi.listAdminParcels.mock.calls.at(-1)[0];

describe('TST-040 Member #5 admin parcels page (FR-PAR-011)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    pricingApi.listZones.mockResolvedValue([
      { id: 'z1', code: 'WESTERN', name: 'Western', active: true,
        districts: [{ code: 'COLOMBO', name: 'Colombo' }, { code: 'GAMPAHA', name: 'Gampaha' }] },
    ]);
    parcelsApi.listAdminParcels.mockResolvedValue({
      items: [parcel(), parcel({ id: 'p-2', trackingNumber: 'SD-20260101-WXYZ', status: 'FAILED', ownerType: 'CUSTOMER',
        merchantReference: null })],
      page: 0, size: 20, totalItems: 2, totalPages: 1,
    });
  });

  it('lists parcels with status chip, owner, route, fee and a link to the detail page', async () => {
    renderPage();
    const link = await screen.findByRole('link', { name: 'SD-20260101-ABCD' });
    expect(link).toHaveAttribute('href', '/admin/parcels/p-1');
    const table = within(screen.getByRole('table', { name: 'Parcels' }));
    expect(table.getByText('In Transit')).toBeInTheDocument();
    expect(table.getByText('Failed')).toBeInTheDocument();
    expect(table.getAllByText('Colombo → Kandy')).toHaveLength(2);
    expect(table.getAllByText('LKR 800.00')).toHaveLength(2);
    expect(table.getByText('ORD-77')).toBeInTheDocument();
    expect(lastCall()).toMatchObject({ page: 0, size: 20, sort: 'createdAt,desc' });
  });

  it('sends every filter of FR-PAR-011 to the API only when Apply is pressed, and resets to page 0', async () => {
    renderPage();
    await screen.findByRole('link', { name: 'SD-20260101-ABCD' });
    // Wait until the district options (loaded from the zones endpoint) are in both district selects.
    await waitFor(() => expect(screen.getAllByRole('option', { name: 'Gampaha', hidden: true })).toHaveLength(2));
    const callsBefore = parcelsApi.listAdminParcels.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'FAILED' } });
    fireEvent.change(screen.getByLabelText('Owner type'), { target: { value: 'MERCHANT' } });
    fireEvent.change(screen.getByLabelText('Tracking number or merchant reference'), { target: { value: ' ORD-7 ' } });
    fireEvent.change(screen.getByLabelText('Origin district'), { target: { value: 'COLOMBO' } });
    fireEvent.change(screen.getByLabelText('Destination district'), { target: { value: 'GAMPAHA' } });
    fireEvent.change(screen.getByLabelText('Created from'), { target: { value: '2026-03-01' } });
    fireEvent.change(screen.getByLabelText('Created to'), { target: { value: '2026-03-31' } });
    expect(parcelsApi.listAdminParcels.mock.calls.length).toBe(callsBefore);

    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(lastCall()).toMatchObject({
      status: 'FAILED', ownerType: 'MERCHANT', q: 'ORD-7', originDistrictCode: 'COLOMBO',
      destinationDistrictCode: 'GAMPAHA', from: '2026-03-01', to: '2026-03-31', page: 0,
    }));
  });

  it('refuses a date range whose from is after to, without calling the API', async () => {
    renderPage();
    await screen.findByRole('link', { name: 'SD-20260101-ABCD' });
    const callsBefore = parcelsApi.listAdminParcels.mock.calls.length;
    fireEvent.change(screen.getByLabelText('Created from'), { target: { value: '2026-03-10' } });
    fireEvent.change(screen.getByLabelText('Created to'), { target: { value: '2026-03-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(await screen.findByText(/must not be after/i)).toBeInTheDocument();
    expect(parcelsApi.listAdminParcels.mock.calls.length).toBe(callsBefore);
  });

  it('sorts by a whitelisted column when its header is clicked', async () => {
    renderPage();
    await screen.findByRole('link', { name: 'SD-20260101-ABCD' });
    fireEvent.click(screen.getByText('Tracking number'));
    await waitFor(() => expect(lastCall().sort).toBe('trackingNumber,asc'));
    fireEvent.click(screen.getByText('Tracking number'));
    await waitFor(() => expect(lastCall().sort).toBe('trackingNumber,desc'));
  });

  it('shows an empty state when nothing matches', async () => {
    parcelsApi.listAdminParcels.mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    renderPage();
    expect(await screen.findByText('No parcels found')).toBeInTheDocument();
  });

  it('shows the server error with a retry button', async () => {
    parcelsApi.listAdminParcels.mockRejectedValueOnce({ detail: 'Parcel service is down' });
    renderPage();
    expect(await screen.findByText('Parcel service is down')).toBeInTheDocument();
    parcelsApi.listAdminParcels.mockResolvedValue({ items: [parcel()], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByRole('link', { name: 'SD-20260101-ABCD' })).toBeInTheDocument();
  });
});
