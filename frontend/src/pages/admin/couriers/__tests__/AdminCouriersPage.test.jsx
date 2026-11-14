import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as deliveryApi from '../../../../api/adminDelivery';
import AdminCouriersPage from '../index.jsx';

vi.mock('../../../../api/adminDelivery');

const couriers = [
  {
    id: 'busy',
    fullName: 'Nimal Silva',
    vehicleType: 'VAN',
    vehicleNumber: 'WP-VAN-1',
    status: 'ACTIVE',
    activeDeliveries: { CREATED: 2, PICKED_UP: 1, IN_TRANSIT: 1, OUT_FOR_DELIVERY: 0 },
    deliveredToday: 3,
    failedToday: 1,
  },
  {
    id: 'idle',
    fullName: 'Amal Perera',
    vehicleType: 'MOTORBIKE',
    vehicleNumber: 'WP-BIKE-1',
    status: 'DEACTIVATED',
    activeDeliveries: { CREATED: 0, PICKED_UP: 0, IN_TRANSIT: 0, OUT_FOR_DELIVERY: 0 },
    deliveredToday: 0,
    failedToday: 0,
  },
];

const delivery = {
  parcelId: 'parcel-1',
  trackingNumber: 'SD-20261114-ABCD',
  status: 'IN_TRANSIT',
  pickupCity: 'Colombo',
  dropoffCity: 'Kandy',
  originDistrictName: 'Colombo',
  destinationDistrictName: 'Kandy',
  assignedAt: '2026-11-14T03:00:00Z',
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <AdminCouriersPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

function pageResponse(items = [delivery], totalItems = items.length) {
  return { items, page: 0, size: 20, totalItems, totalPages: totalItems ? 1 : 0 };
}

describe('TST-040 Member #7 courier workloads', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    deliveryApi.listCouriers.mockResolvedValue(couriers);
    deliveryApi.listCourierDeliveries.mockResolvedValue(pageResponse());
  });

  it('sorts couriers by the sum of activeDeliveries statuses and shows workload details and text statuses', async () => {
    renderPage();
    const table = within(await screen.findByRole('table', { name: 'Courier workloads' }));
    const rows = table.getAllByRole('row');
    const idleCells = within(rows[1]).getAllByRole('cell');
    const busyCells = within(rows[2]).getAllByRole('cell');
    expect(within(rows[1]).getByText('Amal Perera')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Deactivated')).toBeInTheDocument();
    expect(idleCells[3]).toHaveTextContent('0');
    expect(idleCells[5]).toHaveTextContent('0');
    expect(idleCells[6]).toHaveTextContent('0');
    expect(within(rows[2]).getByText('Nimal Silva')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Active')).toBeInTheDocument();
    expect(busyCells[3]).toHaveTextContent('4');
    expect(within(rows[2]).getByText('Created: 2 · Picked Up: 1 · In Transit: 1 · Out For Delivery: 0')).toBeInTheDocument();
    expect(busyCells[5]).toHaveTextContent('3');
    expect(busyCells[6]).toHaveTextContent('1');
    expect(deliveryApi.listCouriers).toHaveBeenCalled();
  });

  it('loads each delivery scope using the selected courier id and resets pagination when scope changes', async () => {
    deliveryApi.listCourierDeliveries.mockResolvedValue(pageResponse([delivery], 40));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'View deliveries for Nimal Silva' }));
    expect(await screen.findByRole('table', { name: 'today deliveries for Nimal Silva' })).toBeInTheDocument();
    expect(deliveryApi.listCourierDeliveries).toHaveBeenCalledWith('busy', { scope: 'today', page: 0, size: 20 });

    fireEvent.click(screen.getByRole('button', { name: 'Go to next page' }));
    await waitFor(() => expect(deliveryApi.listCourierDeliveries).toHaveBeenCalledWith(
      'busy', { scope: 'today', page: 1, size: 20 },
    ));
    fireEvent.click(screen.getByRole('tab', { name: 'Active' }));
    await waitFor(() => expect(deliveryApi.listCourierDeliveries).toHaveBeenCalledWith(
      'busy', { scope: 'active', page: 0, size: 20 },
    ));
    fireEvent.click(screen.getByRole('tab', { name: 'Completed' }));
    await waitFor(() => expect(deliveryApi.listCourierDeliveries).toHaveBeenCalledWith(
      'busy', { scope: 'completed', page: 0, size: 20 },
    ));
  });

  it('paginates deliveries and resets the page when selecting another courier', async () => {
    deliveryApi.listCourierDeliveries.mockResolvedValue(pageResponse([delivery], 40));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'View deliveries for Nimal Silva' }));
    await screen.findByRole('table', { name: 'today deliveries for Nimal Silva' });
    fireEvent.click(screen.getByRole('button', { name: 'Go to next page' }));

    await waitFor(() => expect(deliveryApi.listCourierDeliveries).toHaveBeenCalledWith(
      'busy', { scope: 'today', page: 1, size: 20 },
    ));
    fireEvent.click(screen.getByRole('button', { name: 'View deliveries for Amal Perera' }));
    await waitFor(() => expect(deliveryApi.listCourierDeliveries).toHaveBeenCalledWith(
      'idle', { scope: 'today', page: 0, size: 20 },
    ));
  });

  it('shows delivery fields and handles missing optional values', async () => {
    deliveryApi.listCourierDeliveries.mockResolvedValue(pageResponse([{
      parcelId: 'parcel-minimal',
      trackingNumber: 'SD-MINIMAL',
      status: 'CREATED',
      assignedAt: null,
    }]));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'View deliveries for Nimal Silva' }));
    const table = within(await screen.findByRole('table', { name: 'today deliveries for Nimal Silva' }));
    expect(table.getByText('SD-MINIMAL')).toBeInTheDocument();
    expect(table.getByText('Created')).toBeInTheDocument();
    expect(table.getByText('—')).toBeInTheDocument();
  });

  it('shows loading, empty, and retryable errors for courier workloads', async () => {
    deliveryApi.listCouriers.mockReturnValueOnce(new Promise(() => {}));
    renderPage();
    expect(await screen.findByText('Loading courier workloads...')).toBeInTheDocument();
  });

  it('shows an empty state when there are no couriers', async () => {
    deliveryApi.listCouriers.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No couriers found')).toBeInTheDocument();
  });

  it('shows a courier workload error and retries', async () => {
    deliveryApi.listCouriers.mockRejectedValueOnce({ detail: 'Courier service unavailable' });
    renderPage();
    expect(await screen.findByText('Courier service unavailable')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByRole('table', { name: 'Courier workloads' })).toBeInTheDocument();
  });

  it('shows loading, empty, and retryable errors for courier deliveries', async () => {
    deliveryApi.listCourierDeliveries.mockReturnValueOnce(new Promise(() => {}));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'View deliveries for Nimal Silva' }));
    expect(await screen.findByText('Loading courier deliveries...')).toBeInTheDocument();
  });

  it('shows an empty state when the selected courier has no scoped deliveries', async () => {
    deliveryApi.listCourierDeliveries.mockResolvedValue(pageResponse([]));
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'View deliveries for Nimal Silva' }));
    expect(await screen.findByText('No today deliveries')).toBeInTheDocument();
  });

  it('shows a courier delivery error and retries', async () => {
    deliveryApi.listCourierDeliveries.mockRejectedValueOnce({ detail: 'Deliveries unavailable' });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'View deliveries for Nimal Silva' }));
    expect(await screen.findByText('Deliveries unavailable')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByRole('table', { name: 'today deliveries for Nimal Silva' })).toBeInTheDocument();
  });
});
