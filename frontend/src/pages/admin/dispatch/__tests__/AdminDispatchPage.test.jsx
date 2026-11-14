
import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as deliveryApi from '../../../../api/adminDelivery';
import AdminDispatchPage from '../index.jsx';

vi.mock('../../../../api/adminDelivery');

const courier = {
  id: 'courier-active',
  fullName: 'Amal Perera',
  vehicleType: 'MOTORBIKE',
  vehicleNumber: 'WP-ABC-1234',
  status: 'ACTIVE',
  activeDeliveries: {
    CREATED: 1,
    PICKED_UP: 1,
    IN_TRANSIT: 2,
    OUT_FOR_DELIVERY: 0,
  },
};

const parcel = (overrides = {}) => ({
  parcelId: 'parcel-1',
  trackingNumber: 'SD-20261114-ABCD',
  status: 'CREATED',
  courierId: null,
  originDistrictName: 'Colombo',
  destinationDistrictName: 'Kandy',
  pickupCity: 'Dehiwala',
  dropoffCity: 'Peradeniya',
  parcelCreatedAt: '2026-11-14T03:00:00Z',
  ...overrides,
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <AdminDispatchPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

function queueResponse(items = [parcel()]) {
  return {
    items,
    page: 0,
    size: 20,
    totalItems: items.length,
    totalPages: items.length ? 1 : 0,
  };
}

async function openAssignDialog() {
  const assignButton = await screen.findByRole('button', {
    name: 'Assign',
  });

  fireEvent.click(assignButton);

  const dialog = await screen.findByRole('dialog', {
    name: 'Assign courier',
  });

  const courierRadio = await within(dialog).findByRole('radio', {
    name: /Amal Perera/i,
  });

  return { dialog, courierRadio };
}

function reviewAssignment(dialog, courierRadio) {
  fireEvent.click(courierRadio);

  fireEvent.click(
    within(dialog).getByRole('button', {
      name: 'Review assignment',
    }),
  );
}

function confirmAssignment(dialog) {
  fireEvent.click(
    within(dialog).getByRole('button', {
      name: 'Confirm assignment',
    }),
  );
}

describe('TST-040 Member #7 dispatch queue', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    deliveryApi.listDeliveries.mockImplementation(({ assigned }) =>
      Promise.resolve(
        queueResponse(
          assigned
            ? [parcel({ courierId: courier.id })]
            : [parcel()],
        ),
      ),
    );

    deliveryApi.listCouriers.mockResolvedValue([
      courier,
      {
        ...courier,
        id: 'inactive',
        fullName: 'Inactive Courier',
        status: 'DEACTIVATED',
      },
    ]);

    deliveryApi.assignDelivery.mockResolvedValue({});
  });

  it('requests the unassigned queue with assigned=false and status=CREATED and shows parcel details', async () => {
    renderPage();

    const card = await screen.findByRole('article', {
      name: 'Parcel SD-20261114-ABCD',
    });

    expect(deliveryApi.listDeliveries).toHaveBeenCalledWith(
      expect.objectContaining({
        assigned: false,
        status: 'CREATED',
        page: 0,
        size: 20,
      }),
    );

    expect(within(card).getByText('Colombo')).toBeInTheDocument();
    expect(within(card).getByText('Kandy')).toBeInTheDocument();
    expect(within(card).getByText('Dehiwala')).toBeInTheDocument();
    expect(within(card).getByText('Peradeniya')).toBeInTheDocument();
    expect(within(card).getByText('Created')).toBeInTheDocument();
    expect(within(card).getByText('Age since creation')).toBeInTheDocument();

    expect(
      within(card).getByRole('button', { name: 'Assign' }),
    ).toBeInTheDocument();
  });

  it('requests the assigned CREATED queue and exposes Reassign only on that tab', async () => {
    renderPage();

    await screen.findByRole('article', {
      name: 'Parcel SD-20261114-ABCD',
    });

    expect(
      screen.queryByRole('button', { name: 'Reassign' }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('tab', {
        name: 'Assigned, not picked up',
      }),
    );

    expect(
      await screen.findByRole('button', { name: 'Reassign' }),
    ).toBeInTheDocument();

    expect(deliveryApi.listDeliveries).toHaveBeenCalledWith(
      expect.objectContaining({
        assigned: true,
        status: 'CREATED',
        page: 0,
        size: 20,
      }),
    );

    expect(
      screen.queryByRole('button', { name: 'Assign' }),
    ).not.toBeInTheDocument();
  });

  it('shows a loading state while the queue request is pending', async () => {
    deliveryApi.listDeliveries.mockReturnValueOnce(
      new Promise(() => {}),
    );

    renderPage();

    expect(
      await screen.findByText(/loading unassigned deliveries\.\.\./i),
    ).toBeInTheDocument();
  });

  it('shows an empty state when the queue has no parcels', async () => {
    deliveryApi.listDeliveries.mockResolvedValue(queueResponse([]));

    renderPage();

    expect(
      await screen.findByText('No unassigned parcels'),
    ).toBeInTheDocument();
  });

  it('shows queue errors and retries after a failure', async () => {
    let unassignedCalls = 0;

    deliveryApi.listDeliveries.mockImplementation(({ assigned }) => {
      if (assigned) {
        return Promise.resolve(
          queueResponse([parcel({ courierId: courier.id })]),
        );
      }

      unassignedCalls += 1;

      if (unassignedCalls === 1) {
        return Promise.reject({
          detail: 'Delivery service is unavailable',
        });
      }

      return Promise.resolve(queueResponse());
    });

    renderPage();

    expect(
      await screen.findByText('Delivery service is unavailable'),
    ).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', { name: /try again/i }),
    );

    expect(
      await screen.findByRole('button', { name: 'Assign' }),
    ).toBeInTheDocument();
  });

  it('loads ACTIVE couriers with vehicle and workload details and requires confirmation before assignment', async () => {
    renderPage();

    const { dialog, courierRadio } = await openAssignDialog();

    expect(deliveryApi.listCouriers).toHaveBeenCalled();
    expect(courierRadio).toBeInTheDocument();

    expect(
      within(dialog).getByText('Amal Perera'),
    ).toBeInTheDocument();

    expect(
      within(dialog).getByText('MOTORBIKE · WP-ABC-1234'),
    ).toBeInTheDocument();

    expect(
      within(dialog).getByText('Active delivery workload: 4'),
    ).toBeInTheDocument();

    expect(
      within(dialog).getByText(
        /Created: 1.*Picked Up: 1.*In Transit: 2/,
      ),
    ).toBeInTheDocument();

    expect(
      within(dialog).queryByText('Inactive Courier'),
    ).not.toBeInTheDocument();

    expect(deliveryApi.assignDelivery).not.toHaveBeenCalled();

    reviewAssignment(dialog, courierRadio);

    expect(deliveryApi.assignDelivery).not.toHaveBeenCalled();

    expect(
      within(dialog).getByText(/Confirm assigning this parcel to/i),
    ).toBeInTheDocument();

    expect(
      within(dialog).getByText('Amal Perera'),
    ).toBeInTheDocument();

    expect(
      within(dialog).getByRole('button', {
        name: 'Confirm assignment',
      }),
    ).toBeEnabled();

    expect(deliveryApi.assignDelivery).not.toHaveBeenCalled();
  });

  it('assigns after explicit confirmation, names the courier in success, and refreshes the queue', async () => {
    renderPage();

    await screen.findByRole('button', { name: 'Assign' });

    const initialQueueCalls =
      deliveryApi.listDeliveries.mock.calls.length;

    const { dialog, courierRadio } = await openAssignDialog();

    reviewAssignment(dialog, courierRadio);
    confirmAssignment(dialog);

    await waitFor(() => {
      expect(deliveryApi.assignDelivery).toHaveBeenCalledWith(
        'parcel-1',
        'courier-active',
      );
    });

    expect(
      await screen.findByText('Assigned the parcel to Amal Perera.'),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(
        deliveryApi.listDeliveries.mock.calls.length,
      ).toBeGreaterThan(initialQueueCalls);
    });

    await waitFor(() => {
      expect(
        screen.queryByRole('dialog'),
      ).not.toBeInTheDocument();
    });
  });

  it('shows PARCEL_NOT_ASSIGNABLE and refreshes the queue after the conflict', async () => {
    deliveryApi.assignDelivery.mockRejectedValue({
      code: 'PARCEL_NOT_ASSIGNABLE',
      status: 409,
    });

    renderPage();

    await screen.findByRole('button', { name: 'Assign' });

    const initialQueueCalls =
      deliveryApi.listDeliveries.mock.calls.length;

    const { dialog, courierRadio } = await openAssignDialog();

    reviewAssignment(dialog, courierRadio);
    confirmAssignment(dialog);

    expect(
      await within(dialog).findByText(
        'This parcel can no longer be assigned',
      ),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(
        deliveryApi.listDeliveries.mock.calls.length,
      ).toBeGreaterThan(initialQueueCalls);
    });
  });

  it('shows a clear message when the parcel or courier is not found', async () => {
    deliveryApi.assignDelivery.mockRejectedValue({
      code: 'NOT_FOUND',
      status: 404,
    });

    renderPage();

    const { dialog, courierRadio } = await openAssignDialog();

    reviewAssignment(dialog, courierRadio);
    confirmAssignment(dialog);

    expect(
      await within(dialog).findByText(
        /parcel or courier was not found/i,
      ),
    ).toBeInTheDocument();
  });
});
