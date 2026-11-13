import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../../theme/theme';
import * as parcelsApi from '../../../../../api/adminParcels';
import AdminParcelDetailPage from '../index.jsx';
import { cancelSchema, failSchema } from '../ParcelActionDialogs.jsx';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../../api/adminParcels');
vi.mock('../../../../courier/components/ProofViewer.jsx', () => ({
  ProofViewer: ({ parcelId }) => <div>Proof viewer for {parcelId}</div>,
}));

const detail = (over = {}) => ({
  id: 'p-1',
  trackingNumber: 'SD-20260101-ABCD',
  status: 'CREATED',
  ownerType: 'MERCHANT',
  merchantReference: 'ORD-77',
  pickup: { contactName: 'Nimal', contactPhone: '+94771234567', addressLine1: '1 Main St', city: 'Colombo',
    districtName: 'Colombo' },
  dropoff: { contactName: 'Kamala', contactPhone: '+94777654321', addressLine1: '2 Hill Rd', city: 'Kandy',
    districtName: 'Kandy', contactEmail: 'k@example.com' },
  parcel: { weightKg: '2.00', size: 'MEDIUM', description: 'Books' },
  deliveryInstructions: 'Ring twice',
  originZone: { code: 'WESTERN', name: 'Western' },
  destinationZone: { code: 'CENTRAL', name: 'Central' },
  fee: { currency: 'LKR', baseFee: '500.00', weightFee: '150.00', sizeSurcharge: '150.00', discountAmount: '0.00',
    total: '800.00' },
  createdAt: '2026-03-01T18:45:00Z',
  updatedAt: '2026-03-01T19:00:00Z',
  customerId: null,
  merchantId: 'm-9',
  ...over,
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={['/admin/parcels/p-1']}>
          <Routes>
            <Route path="/admin/parcels/:id" element={<AdminParcelDetailPage />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

const ok = { id: 'p-1', trackingNumber: 'SD-20260101-ABCD', status: 'CANCELLED', statusChangedAt: '2026-03-02T00:00:00Z' };

describe('TST-040 Member #5 admin parcel detail (FR-PAR-011, FR-PAR-012)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    parcelsApi.getAdminParcel.mockResolvedValue(detail());
  });

  it('shows the parcel with contacts, fee, owner ids and placeholders for sections not available yet', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'SD-20260101-ABCD' })).toBeInTheDocument();
    expect(screen.getByText('Nimal')).toBeInTheDocument();
    expect(screen.getByText('k@example.com')).toBeInTheDocument();
    expect(screen.getByText('LKR 800.00')).toBeInTheDocument();
    expect(screen.getByText('m-9')).toBeInTheDocument();
    expect(screen.getByText('ORD-77')).toBeInTheDocument();
    expect(screen.getByText('Timeline not available yet')).toBeInTheDocument();
    expect(screen.getByText('Delivery information not available yet')).toBeInTheDocument();
  });

  it.each([
    ['CREATED', true, false],
    ['PICKED_UP', false, true],
    ['IN_TRANSIT', false, true],
    ['OUT_FOR_DELIVERY', false, true],
    ['DELIVERED', false, false],
    ['FAILED', false, false],
    ['CANCELLED', false, false],
  ])('offers cancel only for CREATED and fail only after pickup: %s', async (status, cancel, fail) => {
    parcelsApi.getAdminParcel.mockResolvedValue(detail({ status }));
    renderPage();
    await screen.findByRole('heading', { name: 'SD-20260101-ABCD' });
    expect(Boolean(screen.queryByRole('button', { name: 'Cancel parcel' }))).toBe(cancel);
    expect(Boolean(screen.queryByRole('button', { name: 'Mark as failed' }))).toBe(fail);
  });

  it('shows the proof viewer only when the parcel is DELIVERED', async () => {
    parcelsApi.getAdminParcel.mockResolvedValue(detail({ status: 'DELIVERED' }));
    renderPage();
    expect(await screen.findByText('Proof viewer for p-1')).toBeInTheDocument();
  });

  it('shows not found for an unknown parcel', async () => {
    parcelsApi.getAdminParcel.mockRejectedValue({ status: 404, detail: 'Parcel not found' });
    renderPage();
    // The error title and the server message both read "Parcel not found".
    expect((await screen.findAllByText('Parcel not found')).length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByRole('button', { name: 'Cancel parcel' })).not.toBeInTheDocument();
  });

  it('cancel: validates the reason (10-300), then needs a confirmation before calling the API', async () => {
    parcelsApi.cancelAdminParcel.mockResolvedValue(ok);
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel parcel' }));
    const dialog = await screen.findByRole('dialog');

    fireEvent.change(within(dialog).getByLabelText(/Reason/), { target: { value: 'too short' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Continue' }));
    expect(await within(dialog).findByText('Enter at least 10 characters')).toBeInTheDocument();
    expect(parcelsApi.cancelAdminParcel).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText(/Reason/), { target: { value: 'Customer asked to cancel' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Continue' }));
    const confirm = await screen.findByRole('dialog', { name: 'Cancel this parcel?' });
    expect(parcelsApi.cancelAdminParcel).not.toHaveBeenCalled();

    fireEvent.click(within(confirm).getByRole('button', { name: 'Yes, cancel parcel' }));
    await waitFor(() => expect(parcelsApi.cancelAdminParcel).toHaveBeenCalledWith('p-1', 'Customer asked to cancel'));
    expect(await screen.findByText('The parcel was cancelled.')).toBeInTheDocument();
  });

  it('fail: offers only PARCEL_DAMAGED and OTHER, requires a note (5-500) and confirms', async () => {
    parcelsApi.getAdminParcel.mockResolvedValue(detail({ status: 'OUT_FOR_DELIVERY' }));
    parcelsApi.failAdminParcel.mockResolvedValue({ ...ok, status: 'FAILED' });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Mark as failed' }));
    const dialog = await screen.findByRole('dialog');

    const options = within(dialog).getAllByRole('option').map((o) => o.value);
    expect(options).toEqual(['PARCEL_DAMAGED', 'OTHER']);

    fireEvent.change(within(dialog).getByLabelText(/Note/), { target: { value: 'abc' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Continue' }));
    expect(await within(dialog).findByText('Enter at least 5 characters')).toBeInTheDocument();
    expect(parcelsApi.failAdminParcel).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText(/Failure reason/), { target: { value: 'OTHER' } });
    fireEvent.change(within(dialog).getByLabelText(/Note/), { target: { value: 'Courier lost the parcel' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Continue' }));
    const confirm = await screen.findByRole('dialog', { name: 'Mark this parcel as failed?' });
    fireEvent.click(within(confirm).getByRole('button', { name: 'Yes, mark as failed' }));
    await waitFor(() => expect(parcelsApi.failAdminParcel).toHaveBeenCalledWith('p-1',
      { failureReason: 'OTHER', note: 'Courier lost the parcel' }));
  });

  it('keeps the input and shows the server message when the transition is refused (409)', async () => {
    parcelsApi.cancelAdminParcel.mockRejectedValue({
      status: 409, code: 'INVALID_STATUS_TRANSITION', detail: 'This parcel cannot move from PICKED_UP to CANCELLED.', errors: [],
    });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel parcel' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/Reason/), { target: { value: 'Customer asked to cancel' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Continue' }));
    const confirm = await screen.findByRole('dialog', { name: 'Cancel this parcel?' });
    fireEvent.click(within(confirm).getByRole('button', { name: 'Yes, cancel parcel' }));

    expect(await screen.findByText('This parcel cannot move from PICKED_UP to CANCELLED.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Reason/)).toHaveValue('Customer asked to cancel');
  });
});

describe('TST-040 Member #5 admin action schemas mirror the server limits', () => {
  it('cancel reason is 10-300 characters', () => {
    expect(cancelSchema.safeParse({ reason: 'a'.repeat(9) }).success).toBe(false);
    expect(cancelSchema.safeParse({ reason: 'a'.repeat(10) }).success).toBe(true);
    expect(cancelSchema.safeParse({ reason: 'a'.repeat(300) }).success).toBe(true);
    expect(cancelSchema.safeParse({ reason: 'a'.repeat(301) }).success).toBe(false);
  });

  it('fail needs PARCEL_DAMAGED or OTHER and a note of 5-500 characters', () => {
    expect(failSchema.safeParse({ failureReason: 'RECIPIENT_REFUSED', note: 'valid note' }).success).toBe(false);
    expect(failSchema.safeParse({ failureReason: 'OTHER', note: 'abcd' }).success).toBe(false);
    expect(failSchema.safeParse({ failureReason: 'OTHER', note: 'abcde' }).success).toBe(true);
    expect(failSchema.safeParse({ failureReason: 'PARCEL_DAMAGED', note: 'a'.repeat(501) }).success).toBe(false);
  });
});
