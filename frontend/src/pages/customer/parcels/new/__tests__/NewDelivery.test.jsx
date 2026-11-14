import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import NewParcelPage from '../index';
import { useDeliveryQuote } from '../useDeliveryQuote';
import { getDistricts, postQuote } from '../../../../../api/quotes';
import { createParcel } from '../../../../../api/customerParcels';

const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }));
vi.mock('react-router-dom', () => ({ useNavigate: () => navigate }));
vi.mock('../../../../../api/quotes', () => ({ getDistricts: vi.fn(), postQuote: vi.fn() }));
vi.mock('../../../../../api/customerParcels', () => ({ createParcel: vi.fn() }));

const reference = { maxWeightKg: '17.25', districts: [{ code: 'COLOMBO', name: 'Colombo' }, { code: 'KANDY', name: 'Kandy' }], sizes: [{ size: 'SMALL', description: 'Small box', surcharge: '0.00' }] };
const fee = { currency: 'LKR', baseFee: '500.00', weightFee: '150.00', sizeSurcharge: '0.00', discountAmount: '0.00', total: '650.00' };
const details = { pickup: { districtCode: 'COLOMBO' }, dropoff: { districtCode: 'KANDY' }, parcel: { weightKg: '2.00', size: 'SMALL' } };
const deferred = () => { let resolve; let reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };

beforeEach(() => {
  vi.clearAllMocks();
  getDistricts.mockResolvedValue(reference);
  postQuote.mockResolvedValue(fee);
  createParcel.mockResolvedValue({ id: 'created-id', status: 'CREATED' });
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

async function selectOption(scope, label, name) {
  fireEvent.mouseDown(scope.getByRole('combobox', { name: label }));
  fireEvent.click(await screen.findByRole('option', { name }));
}
async function fillDetails() {
  await screen.findByRole('heading', { name: 'Details' });
  for (const title of ['Pickup', 'Drop-off']) {
    const group = within(screen.getByRole('group', { name: title }));
    for (const [label, value] of [['Contact name', `${title} Person`], ['Phone', '0771234567'], ['Address line 1', '12 Test Road'], ['City', title === 'Pickup' ? 'Colombo' : 'Kandy']]) {
      fireEvent.change(group.getByLabelText(new RegExp(`^${label}`)), { target: { value } });
    }
    await selectOption(group, /District/, title === 'Pickup' ? 'Colombo' : 'Kandy');
  }
  fireEvent.change(screen.getByRole('textbox', { name: /Weight/ }), { target: { value: '2.00' } });
  await selectOption(within(screen.getByRole('group', { name: 'Parcel' })), /Parcel size/, /SMALL/);
}
async function reachQuote() {
  render(<NewParcelPage />);
  await fillDetails();
  fireEvent.click(screen.getByRole('button', { name: 'Get quote' }));
  await screen.findByRole('heading', { name: 'Quote' });
}
async function reachConfirm() {
  await reachQuote();
  const review = await screen.findByRole('button', { name: 'Review delivery' });
  await waitFor(() => expect(review).toBeEnabled());
  fireEvent.click(review);
  await screen.findByRole('heading', { name: 'Confirm' });
}

describe('TST-040 quote debounce and stale-response protection', () => {
  it('waits exactly 400 ms and sends only quote inputs', async () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useDeliveryQuote(details, true, 0));
    await act(() => vi.advanceTimersByTimeAsync(399));
    expect(postQuote).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(postQuote).toHaveBeenCalledWith({ originDistrictCode: 'COLOMBO', destinationDistrictCode: 'KANDY', weightKg: '2.00', size: 'SMALL' });
    expect(result.current.fee).toEqual(fee);
  });
  it('invalidates a quote when inputs change and ignores an older response arriving last', async () => {
    vi.useFakeTimers();
    const old = deferred();
    postQuote.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...fee, total: '800.00' });
    const { result, rerender } = renderHook(({ values }) => useDeliveryQuote(values, true, 0), { initialProps: { values: details } });
    await act(() => vi.advanceTimersByTimeAsync(400));
    rerender({ values: { ...details, parcel: { ...details.parcel, weightKg: '3.00' } } });
    expect(result.current.fee).toBeUndefined();
    await act(() => vi.advanceTimersByTimeAsync(400));
    expect(result.current.fee.total).toBe('800.00');
    await act(async () => old.resolve({ ...fee, total: '1.00' }));
    expect(result.current.fee.total).toBe('800.00');
  });
  it('cancels pending debounce on exit and ignores unmounted in-flight results', async () => {
    vi.useFakeTimers();
    const first = renderHook(() => useDeliveryQuote(details, true, 0));
    first.unmount();
    await act(() => vi.advanceTimersByTimeAsync(400));
    expect(postQuote).not.toHaveBeenCalled();
    const pending = deferred();
    postQuote.mockReturnValue(pending.promise);
    const second = renderHook(() => useDeliveryQuote(details, true, 0));
    await act(() => vi.advanceTimersByTimeAsync(400));
    second.unmount();
    await act(async () => pending.resolve(fee));
    expect(postQuote).toHaveBeenCalledTimes(1);
  });
  it('invalidates a completed quote while disabled and refetches on retry revision', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(({ enabled, revision }) => useDeliveryQuote(details, enabled, revision), { initialProps: { enabled: true, revision: 0 } });
    await act(() => vi.advanceTimersByTimeAsync(400));
    rerender({ enabled: false, revision: 0 });
    expect(result.current.fee).toBeUndefined();
    rerender({ enabled: true, revision: 1 });
    expect(result.current.fee).toBeUndefined();
    await act(() => vi.advanceTimersByTimeAsync(400));
    expect(postQuote).toHaveBeenCalledTimes(2);
  });
});

describe('TST-040 create parcel wizard', () => {
  it('loads backend options, validates required fields and focuses an invalid field', async () => {
    const loading = deferred();
    getDistricts.mockReturnValue(loading.promise);
    render(<NewParcelPage />);
    expect(screen.getByText('Loading delivery options…')).toBeInTheDocument();
    await act(async () => loading.resolve(reference));
    expect(screen.getByText(/Maximum 17.25 kg/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Get quote' }));
    await waitFor(() => expect(screen.getAllByRole('textbox', { name: /Contact name/ })[0].getAttribute('aria-invalid')).toBe('true'));
    await waitFor(() => expect(screen.getAllByRole('textbox', { name: /Contact name/ })[0]).toHaveFocus());
    expect(postQuote).not.toHaveBeenCalled();
  });
  it('recovers from a reference-data failure', async () => {
    getDistricts.mockRejectedValueOnce(new Error('offline'));
    render(<NewParcelPage />);
    await screen.findByText('Delivery options unavailable');
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    await screen.findByRole('heading', { name: 'Details' });
    expect(getDistricts).toHaveBeenCalledTimes(2);
  });
  it('preserves fields on back navigation and requires a new quote after editing', async () => {
    await reachQuote();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review delivery' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Edit details' }));
    expect(screen.getByRole('textbox', { name: /Weight/ })).toHaveValue('2.00');
    fireEvent.change(screen.getByRole('textbox', { name: /Weight/ }), { target: { value: '3.00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Get quote' }));
    await screen.findByRole('heading', { name: 'Quote' });
    expect(screen.getByRole('button', { name: 'Review delivery' })).toBeDisabled();
    await waitFor(() => expect(postQuote).toHaveBeenLastCalledWith(expect.objectContaining({ weightKg: '3.00' })));
  });
  it('displays only server fees and navigates after confirmed creation', async () => {
    const uuid = vi.spyOn(crypto, 'randomUUID');
    await reachConfirm();
    expect(uuid).toHaveBeenCalledTimes(1);
    expect(screen.getByText('LKR 650.00')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Confirm' })).toHaveFocus();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivery' }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/customer/parcels/created-id', { replace: true }));
    expect(createParcel).toHaveBeenCalledWith(expect.objectContaining({ expectedFee: '650.00', parcel: { weightKg: '2.00', size: 'SMALL' } }), expect.any(String));
  });
  it('blocks double submission while pending', async () => {
    const pending = deferred();
    createParcel.mockReturnValue(pending.promise);
    await reachConfirm();
    const button = screen.getByRole('button', { name: 'Confirm delivery' });
    fireEvent.click(button);
    fireEvent.click(button);
    expect(createParcel).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Submitting…' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Edit details' })).toBeDisabled();
    expect(navigate).not.toHaveBeenCalled();
    await act(async () => pending.resolve({ id: 'one', status: 'CREATED' }));
  });
  it.each([new TypeError('Network failure'), { status: 503 }, new SyntaxError('Invalid response')])('retries an uncertain result with the same frozen body and key', async (failure) => {
    createParcel.mockRejectedValueOnce(failure).mockResolvedValueOnce({ id: 'one', status: 'CREATED' });
    const uuid = vi.spyOn(crypto, 'randomUUID');
    await reachConfirm();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivery' }));
    const retry = await screen.findByRole('button', { name: 'Retry same request' });
    expect(screen.getByRole('button', { name: 'Edit details' })).toBeDisabled();
    expect(navigate).not.toHaveBeenCalled();
    fireEvent.click(retry);
    await waitFor(() => expect(navigate).toHaveBeenCalled());
    expect(createParcel.mock.calls[1]).toEqual(createParcel.mock.calls[0]);
    expect(uuid).toHaveBeenCalledTimes(1);
  });
  it.each([undefined, { ...fee, total: '900.00' }])('recovers from FEE_CHANGED safely and requires a new confirmation key', async (updatedFee) => {
    createParcel.mockRejectedValueOnce({ status: 409, code: 'FEE_CHANGED', fee: updatedFee });
    const uuid = vi.spyOn(crypto, 'randomUUID');
    await reachConfirm();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivery' }));
    await screen.findByRole('heading', { name: 'Quote' });
    expect(screen.getByRole('alert')).toHaveTextContent('fee changed');
    expect(navigate).not.toHaveBeenCalled();
    if (!updatedFee) expect(screen.getByRole('button', { name: 'Review delivery' })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review delivery' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Review delivery' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivery' }));
    await waitFor(() => expect(createParcel).toHaveBeenCalledTimes(2));
    expect(uuid).toHaveBeenCalledTimes(2);
    expect(createParcel.mock.calls[0][1]).not.toBe(createParcel.mock.calls[1][1]);
    expect(createParcel.mock.calls[1][0].expectedFee).toBe(updatedFee?.total || fee.total);
  });
  it('maps server validation to fields without losing input', async () => {
    createParcel.mockRejectedValue({ status: 400, detail: 'Check weight', errors: [{ field: 'weightKg', message: 'Weight not accepted' }] });
    await reachConfirm();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delivery' }));
    await screen.findByRole('heading', { name: 'Details' });
    expect(screen.getByRole('textbox', { name: /Weight/ })).toHaveValue('2.00');
    expect(screen.getByText('Weight not accepted')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });
  it.each([
    ['ZONE_NOT_SERVICEABLE', 'not currently serviceable'],
    ['PRICING_NOT_CONFIGURED', 'Pricing is not available'],
    ['SERVICE_UNAVAILABLE', 'could not obtain a quotation'],
  ])('shows %s quote errors and allows retry', async (code, message) => {
    postQuote.mockRejectedValueOnce({ code });
    await reachQuote();
    await screen.findByText(new RegExp(message));
    expect(screen.getByRole('button', { name: 'Review delivery' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Review delivery' })).toBeEnabled());
  });
  it('supports keyboard focus on labelled fields', async () => {
    const user = userEvent.setup();
    render(<NewParcelPage />);
    await screen.findByRole('heading', { name: 'Details' });
    await user.tab();
    expect(within(screen.getByRole('group', { name: 'Pickup' })).getByRole('textbox', { name: /Contact name/ })).toHaveFocus();
    await user.keyboard('Keyboard Name');
    expect(screen.getByDisplayValue('Keyboard Name')).toHaveFocus();
  });
});
