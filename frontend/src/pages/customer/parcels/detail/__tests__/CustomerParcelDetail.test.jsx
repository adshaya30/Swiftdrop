import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Page from '../index';
import { useParcelTimeline } from '../useParcelTimeline';
import { ownerIntegrations, useCustomerStatusStream } from '../integrations';
import { getParcel, cancelParcel } from '../../../../../api/customerParcels';
import { getProof, getProofPhoto } from '../../../../../api/proofs';
import { formatDateTime } from '../../../../../utils/format';

vi.mock('../../../../../api/customerParcels', () => ({ getParcel: vi.fn(), cancelParcel: vi.fn() }));
vi.mock('../../../../../api/proofs', () => ({ getProof: vi.fn(), getProofPhoto: vi.fn() }));
vi.mock('../integrations', async () => {
  const { default: ProofViewer } = await import('../../../../courier/components/ProofViewer');
  return {
    hasCustomerStatusStream: true,
    useCustomerStatusStream: vi.fn(),
    ownerIntegrations: {
      ProofViewer,
      getTimeline: vi.fn(),
      ParcelTimeline: ({ timeline }) => <p>Timeline entries: {timeline.events.length}</p>,
      ReceiptButton: null,
    },
  };
});

const parcel = (status = 'CREATED', id = 'one') => ({ id, ownerType: 'CUSTOMER', trackingNumber: 'SD-0123456789', status,
  pickup: { contactName: 'Pickup Person', contactPhone: '+94771234567', addressLine1: '12 Test Road', addressLine2: 'Floor 2', city: 'Colombo', districtName: 'Colombo', districtCode: 'COLOMBO', postalCode: '00100' },
  dropoff: { contactName: 'Dropoff Person', contactPhone: '+94771234568', contactEmail: 'test@example.com', addressLine1: '24 Test Road', city: 'Kandy', districtCode: 'KANDY', districtName: 'Kandy' },
  parcel: { weightKg: '2.00', size: 'SMALL', description: 'Books' }, deliveryInstructions: 'Ring bell', originZone: { name: 'Western' }, destinationZone: { name: 'Central' },
  fee: { currency: 'LKR', baseFee: '500.00', weightFee: '150.00', sizeSurcharge: '0.00', discountAmount: '0.00', total: '650.00' },
  createdAt: '2026-11-14T00:00:00Z', updatedAt: '2026-11-14T01:00:00Z' });
const deferred = () => { let resolve; let reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
const timelineLoader = ownerIntegrations.getTimeline;
const timelineComponent = ownerIntegrations.ParcelTimeline;
let client;
function mount() {
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/customer/parcels/one']}><Routes><Route path="/customer/parcels/:id" element={<Page />} /></Routes></MemoryRouter></QueryClientProvider>);
}
beforeEach(() => {
  vi.clearAllMocks();
  getParcel.mockReset().mockResolvedValue(parcel());
  cancelParcel.mockReset().mockResolvedValue({ id: 'one', status: 'CANCELLED' });
  timelineLoader.mockReset().mockResolvedValue({ events: [] });
  ownerIntegrations.getTimeline = timelineLoader;
  ownerIntegrations.ParcelTimeline = timelineComponent;
  ownerIntegrations.ReceiptButton = null;
  getProof.mockReset().mockResolvedValue({ recipientName: 'Receiver', hasPhoto: false, capturedAt: parcel().updatedAt });
});
afterEach(() => { cleanup(); client?.clear(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe('TST-040 customer parcel detail and cancellation', () => {
  it('shows loading, complete customer details and server fees', async () => {
    const pending = deferred(); getParcel.mockReturnValue(pending.promise);
    mount(); expect(screen.getByText('Loading parcel details…')).toBeInTheDocument();
    await act(async () => pending.resolve(parcel()));
    expect(screen.getByText('SD-0123456789')).toBeInTheDocument();
    expect(screen.getByText('Pickup Person')).toBeInTheDocument();
    expect(screen.getByText('test@example.com')).toBeInTheDocument();
    expect(screen.getByText(/12 Test Road, Floor 2/)).toBeInTheDocument();
    expect(screen.getByText('2.00 kg')).toBeInTheDocument();
    expect(screen.getByText('LKR 650.00')).toBeInTheDocument();
    expect(screen.getByText(`Created: ${formatDateTime(parcel().createdAt)}`)).toBeInTheDocument();
    expect(getParcel).toHaveBeenCalledWith('one');
  });
  it.each([404, 401, 403, 500])('handles %s without exposing stale or raw data and permits retry', async (status) => {
    getParcel.mockRejectedValueOnce({ status, detail: 'PRIVATE BODY' });
    mount(); await screen.findByRole('alert');
    expect(screen.queryByText('PRIVATE BODY')).not.toBeInTheDocument();
    expect(screen.queryByText('Pickup Person')).not.toBeInTheDocument();
    if (status === 404) expect(screen.getByText('Parcel not found')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    await screen.findByText('Pickup Person');
  });
  it('rejects a mismatched id or non-customer payload', async () => {
    getParcel.mockResolvedValue({ ...parcel(), id: 'foreign', ownerType: 'MERCHANT' });
    mount(); await screen.findByText('Parcel not found');
    expect(screen.queryByText('Pickup Person')).not.toBeInTheDocument();
    expect(getProof).not.toHaveBeenCalled();
  });
  it.each(['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'FAILED'])('hides cancellation for %s', async (status) => {
    getParcel.mockResolvedValue(parcel(status)); mount(); await screen.findByText('Pickup Person');
    expect(screen.queryByRole('button', { name: 'Cancel parcel' })).not.toBeInTheDocument();
  });
  it('requires confirmation, preserves the reason, prevents duplicates and refreshes related queries', async () => {
    const pending = deferred(); cancelParcel.mockReturnValue(pending.promise);
    mount(); await screen.findByText('Pickup Person');
    const invalidate = vi.spyOn(client, 'invalidateQueries');
    fireEvent.change(screen.getByLabelText('Cancellation reason (optional)'), { target: { value: 'Plans changed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    expect(cancelParcel).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Keep parcel' }));
    expect(screen.getByLabelText('Cancellation reason (optional)')).toHaveValue('Plans changed');
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel parcel' }));
    const confirm = screen.getByRole('button', { name: 'Confirm cancellation' });
    fireEvent.click(confirm); fireEvent.click(confirm);
    expect(cancelParcel).toHaveBeenCalledTimes(1);
    expect(cancelParcel).toHaveBeenCalledWith('one', { reason: 'Plans changed' });
    expect(screen.getByRole('button', { name: 'Processing...' })).toBeDisabled();
    getParcel.mockResolvedValue(parcel('CANCELLED'));
    await act(async () => pending.resolve({ id: 'one', status: 'CANCELLED' }));
    await screen.findByText('Your parcel was cancelled.');
    await screen.findByText('Pickup Person');
    expect(screen.queryByRole('button', { name: 'Cancel parcel' })).not.toBeInTheDocument();
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['customer'] });
  });
  it('accepts an empty optional reason and guards reasons over 300 characters', async () => {
    mount(); await screen.findByText('Pickup Person');
    const field = screen.getByLabelText('Cancellation reason (optional)');
    expect(field).toHaveAttribute('maxlength', '300');
    fireEvent.change(field, { target: { value: 'x'.repeat(301) } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    expect(screen.getByText('Reason must be at most 300 characters.')).toBeInTheDocument();
    expect(cancelParcel).not.toHaveBeenCalled();
    fireEvent.change(field, { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await waitFor(() => expect(cancelParcel).toHaveBeenCalledWith('one', {}));
  });
  it('refreshes on INVALID_STATUS_TRANSITION without assuming a lifecycle transition', async () => {
    cancelParcel.mockRejectedValue({ status: 409, code: 'INVALID_STATUS_TRANSITION' });
    mount(); await screen.findByText('Pickup Person');
    getParcel.mockResolvedValue(parcel('PICKED_UP'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await screen.findByText(/cancellation was not accepted/);
    await screen.findByText('Pickup Person');
    expect(screen.queryByRole('button', { name: 'Cancel parcel' })).not.toBeInTheDocument();
  });
  it('accepts a reason exactly 300 characters long', async () => {
    mount(); await screen.findByText('Pickup Person');
    fireEvent.change(screen.getByLabelText('Cancellation reason (optional)'), { target: { value: 'x'.repeat(300) } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await waitFor(() => expect(cancelParcel).toHaveBeenCalledWith('one', { reason: 'x'.repeat(300) }));
  });
  it('preserves the cancellation reason and displays server field errors', async () => {
    cancelParcel.mockRejectedValue({ status: 400, errors: [{ field: 'reason', message: 'Please revise this reason' }] });
    mount(); await screen.findByText('Pickup Person');
    fireEvent.change(screen.getByLabelText('Cancellation reason (optional)'), { target: { value: 'Plans changed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await screen.findByText('Please revise this reason');
    expect(screen.getByLabelText('Cancellation reason (optional)')).toHaveValue('Plans changed');
  });
  it('re-reads server state after an uncertain cancellation failure', async () => {
    cancelParcel.mockRejectedValue(new TypeError('network failure'));
    mount(); await screen.findByText('Pickup Person');
    getParcel.mockResolvedValue(parcel('CANCELLED'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await screen.findByText(/Cancellation could not be confirmed/);
    await screen.findByText('Pickup Person');
    expect(screen.queryByRole('button', { name: 'Cancel parcel' })).not.toBeInTheDocument();
  });
  it('shows timeline loading and error states and permits manual retry', async () => {
    const pending = deferred(); timelineLoader.mockReturnValueOnce(pending.promise);
    mount(); await screen.findByText('Loading timeline…');
    await act(async () => pending.reject({ status: 503 }));
    await screen.findByText('Timeline unavailable');
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    await screen.findByText('Timeline entries: 0');
  });
  it.each([404, 401, 403])('hides details after cancellation and refreshed read fail with %s', async (status) => {
    cancelParcel.mockRejectedValue({ status }); mount(); await screen.findByText('Pickup Person');
    getParcel.mockRejectedValue({ status });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel parcel' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm cancellation' }));
    await screen.findByRole('alert');
    expect(screen.queryByText('Pickup Person')).not.toBeInTheDocument();
  });
  it('shows explicit unavailable integrations without creating replacement components', async () => {
    ownerIntegrations.getTimeline = null; ownerIntegrations.ParcelTimeline = null;
    mount(); await screen.findByText('Parcel timeline is currently unavailable.');
    expect(screen.getByText('Receipt download is currently unavailable.')).toBeInTheDocument();
    expect(timelineLoader).not.toHaveBeenCalled();
  });
  it('refreshes detail, timeline and delivered proof for every isolated live/poll callback', async () => {
    getParcel.mockResolvedValue(parcel('DELIVERED')); mount(); await screen.findByText('Receiver');
    await screen.findByText('Timeline entries: 0');
    for (let index = 0; index < 2; index++) {
      const callbacks = useCustomerStatusStream.mock.calls.at(-1)[1];
      const count = getParcel.mock.calls.length;
      await act(async () => callbacks.onStatus());
      await screen.findByText('Receiver');
      expect(getParcel).toHaveBeenCalledTimes(count + 1);
      expect(timelineLoader).toHaveBeenCalledTimes(count + 1);
      expect(getProof).toHaveBeenCalledTimes(count + 1);
    }
  });
  it('shows actual owner proof only when DELIVERED and revokes blob URLs on refresh/unmount', async () => {
    const create = vi.fn().mockReturnValue('blob:proof'); const revoke = vi.fn();
    class TestURL extends URL {}
    TestURL.createObjectURL = create; TestURL.revokeObjectURL = revoke;
    vi.stubGlobal('URL', TestURL);
    getProof.mockResolvedValue({ recipientName: 'Receiver', hasPhoto: true });
    getProofPhoto.mockResolvedValue(new Blob(['photo'], { type: 'image/png' }));
    getParcel.mockResolvedValue(parcel('DELIVERED')); const view = mount();
    const image = await screen.findByRole('img', { name: 'Proof of Delivery' });
    expect(image).toHaveAttribute('src', 'blob:proof');
    expect(getProofPhoto).toHaveBeenCalledWith('one');
    fireEvent.click(screen.getByRole('button', { name: 'Refresh parcel' }));
    await screen.findByRole('img', { name: 'Proof of Delivery' });
    expect(revoke).toHaveBeenCalledWith('blob:proof');
    view.unmount(); expect(revoke).toHaveBeenCalledTimes(2);
  });
  it('does not request proof before delivery and can compose an isolated receipt contract', async () => {
    ownerIntegrations.ReceiptButton = ({ parcelId }) => <button>Receipt for {parcelId}</button>;
    mount(); await screen.findByRole('button', { name: 'Receipt for one' });
    expect(getProof).not.toHaveBeenCalled();
    expect(screen.queryByRole('region', { name: 'Delivery proof' })).not.toBeInTheDocument();
  });
  it('supports keyboard cancellation dialog focus and dismissal', async () => {
    const user = userEvent.setup(); mount(); await screen.findByText('Pickup Person');
    act(() => screen.getByRole('button', { name: 'Cancel parcel' }).focus());
    await user.keyboard('{Enter}');
    await screen.findByRole('dialog');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});

describe('TST-040 authenticated timeline adapter retry behavior (isolated contract)', () => {
  it('loads then retries a temporary 404 after exactly two seconds', async () => {
    vi.useFakeTimers(); const load = vi.fn().mockRejectedValueOnce({ status: 404 }).mockResolvedValue({ events: [] });
    const { result } = renderHook(() => useParcelTimeline('one', load, 0));
    await act(async () => {}); expect(result.current.status).toBe('updating');
    await act(() => vi.advanceTimersByTimeAsync(1999)); expect(load).toHaveBeenCalledTimes(1);
    await act(() => vi.advanceTimersByTimeAsync(1)); expect(result.current.status).toBe('success');
  });
  it('stops retrying at 30 seconds and does not extend the window on refresh', async () => {
    vi.useFakeTimers(); const load = vi.fn().mockRejectedValue({ status: 404 });
    const { result, rerender } = renderHook(({ revision }) => useParcelTimeline('one', load, revision), { initialProps: { revision: 0 } });
    await act(() => vi.advanceTimersByTimeAsync(28000));
    rerender({ revision: 1 }); await act(async () => {});
    await act(() => vi.advanceTimersByTimeAsync(2000)); expect(result.current.status).toBe('error');
    const count = load.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(30000)); expect(load).toHaveBeenCalledTimes(count);
  });
  it('does not retry authorization failures and cancels timers on unmount', async () => {
    vi.useFakeTimers(); const load = vi.fn().mockRejectedValue({ status: 403 });
    const first = renderHook(() => useParcelTimeline('one', load, 0));
    await act(async () => {}); expect(first.result.current.status).toBe('error'); first.unmount();
    load.mockRejectedValue({ status: 404 });
    const second = renderHook(() => useParcelTimeline('one', load, 0)); await act(async () => {}); second.unmount();
    const count = load.mock.calls.length;
    await act(() => vi.advanceTimersByTimeAsync(30000)); expect(load).toHaveBeenCalledTimes(count);
  });
  it('ignores obsolete results when the parcel changes', async () => {
    const old = deferred(); const load = vi.fn().mockReturnValueOnce(old.promise).mockResolvedValue({ events: ['new'] });
    const { result, rerender } = renderHook(({ id }) => useParcelTimeline(id, load, 0), { initialProps: { id: 'one' } });
    rerender({ id: 'two' }); await act(async () => {});
    await act(async () => old.resolve({ events: ['old'] })); expect(result.current.data.events).toEqual(['new']);
  });
});
