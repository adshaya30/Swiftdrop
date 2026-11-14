import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../api/adminMerchants', () => ({
  listMerchants: vi.fn(),
  getMerchant: vi.fn(),
  approveMerchant: vi.fn(),
  rejectMerchant: vi.fn(),
  suspendMerchant: vi.fn(),
  reinstateMerchant: vi.fn(),
  setDiscount: vi.fn(),
  pendingCount: vi.fn(),
}));

import {
  listMerchants,
  getMerchant,
  approveMerchant,
  rejectMerchant,
  suspendMerchant,
  reinstateMerchant,
  setDiscount,
  pendingCount,
} from '../api/adminMerchants';
import { ApiError } from '../api/http';
import MerchantDetailPage from '../pages/admin/merchants/detail/index.jsx';
import PendingMerchantsPage from '../pages/admin/merchants/pending/index.jsx';
import PendingMerchantsCard, { PendingMerchantsCard as NamedCard } from '../pages/admin/merchants/PendingMerchantsCard.jsx';

const detail = (overrides = {}) => ({
  id: 'm-1',
  userId: 'u-1',
  businessName: 'Lanka Crafts',
  businessRegistrationNo: 'PV 12345',
  contactName: 'Nimal Perera',
  email: 'shop@example.com',
  phone: '+94771234567',
  addressLine1: '12 Galle Road',
  city: 'Colombo',
  status: 'PENDING',
  statusReason: null,
  discountPercent: 0,
  decidedAt: null,
  decidedBy: null,
  createdAt: '2026-11-10T04:30:00Z',
  updatedAt: '2026-11-10T04:30:00Z',
  ...overrides,
});

const REASON = 'Documents could not be verified';

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/admin/merchants/m-1']}>
        <Routes>
          <Route path="/admin/merchants/:id" element={<MerchantDetailPage />} />
          <Route path="/admin/merchants" element={<div>Directory</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const button = (name) => screen.queryByRole('button', { name });
const dialogOf = () => screen.findByRole('dialog');

describe('TST-040 Member 3: merchant review page (FR-AUTH-017, BR-12, UC-A05)', { timeout: 20000 }, () => {
  beforeEach(() => {
    [listMerchants, getMerchant, approveMerchant, rejectMerchant, suspendMerchant, reinstateMerchant, setDiscount, pendingCount]
      .forEach((mock) => mock.mockReset());
  });

  // ------------------------------------------------------------ data

  it('shows a loading state, then every merchant field with the status as text and icon', async () => {
    let resolve;
    getMerchant.mockImplementation(() => new Promise((r) => { resolve = r; }));
    renderDetail();
    expect(screen.getByText('Loading merchant...')).toBeInTheDocument();

    resolve(detail({ status: 'REJECTED', statusReason: REASON, discountPercent: 12.5, decidedAt: '2026-11-11T04:30:00Z', decidedBy: 'admin-9' }));
    expect(await screen.findByRole('heading', { name: 'Lanka Crafts' })).toBeInTheDocument();
    const details = screen.getByRole('region', { name: 'Merchant details' });
    for (const text of ['PV 12345', 'Nimal Perera', 'shop@example.com', '+94771234567', '12 Galle Road, Colombo', REASON,
      '12.50%', 'admin-9']) {
      expect(within(details).getByText(text)).toBeInTheDocument();
    }
    expect(within(details).getByText('Rejected')).toBeInTheDocument();
    expect(within(details).getByText(/Nov 10, 2026, 10:00\s?AM/)).toBeInTheDocument(); // Asia/Colombo
    expect(within(details).getByText(/Nov 11, 2026, 10:00\s?AM/)).toBeInTheDocument();
    expect(getMerchant).toHaveBeenCalledWith('m-1');
    expect(screen.getByRole('link', { name: /all merchants/i })).toHaveAttribute('href', '/admin/merchants');
  });

  it('shows a dash for missing optional values', async () => {
    getMerchant.mockResolvedValue(detail({ businessRegistrationNo: null, statusReason: null }));
    renderDetail();
    const details = await screen.findByRole('region', { name: 'Merchant details' });
    expect(within(details).getAllByText('—').length).toBeGreaterThanOrEqual(3); // reg no, reason, decision, decided by
  });

  it('a missing merchant shows "Merchant not found" without a retry; other errors can be retried', async () => {
    getMerchant.mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'Merchant not found.' }));
    const { unmount } = renderDetail();
    expect(await screen.findByText('Merchant not found')).toBeInTheDocument();
    expect(button(/try again|retry/i)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /all merchants/i })).toBeInTheDocument();
    unmount();

    getMerchant.mockReset();
    const failure = new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something broke' });
    getMerchant.mockRejectedValueOnce(failure).mockRejectedValueOnce(failure); // the page retries once by itself
    getMerchant.mockResolvedValueOnce(detail());
    const user = userEvent.setup();
    renderDetail();
    expect(await screen.findByText('Could not load this merchant', {}, { timeout: 4000 })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByRole('heading', { name: 'Lanka Crafts' })).toBeInTheDocument();
  });

  // ------------------------------------------------------------ BR-12 action matrix

  it.each([
    ['PENDING', ['Approve', 'Reject']],
    ['REJECTED', ['Approve']],
    ['APPROVED', ['Suspend']],
    ['SUSPENDED', ['Reinstate']],
  ])('BR-12: a %s merchant offers exactly the status actions %j (and always Edit discount)', async (status, expected) => {
    getMerchant.mockResolvedValue(detail({ status }));
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    for (const action of ['Approve', 'Reject', 'Suspend', 'Reinstate']) {
      if (expected.includes(action)) {
        expect(button(action)).toBeInTheDocument();
      } else {
        expect(button(action)).not.toBeInTheDocument();
      }
    }
    expect(button('Edit discount')).toBeInTheDocument();
  });

  // ------------------------------------------------------------ approve and reinstate (ConfirmDialog)

  it('approve needs a confirmation, then updates the status and the buttons without a reload', async () => {
    getMerchant.mockResolvedValueOnce(detail()).mockResolvedValue(detail({ status: 'APPROVED', decidedBy: 'admin-9' }));
    approveMerchant.mockResolvedValue(detail({ status: 'APPROVED', decidedBy: 'admin-9' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Approve'));
    const dialog = await dialogOf();
    expect(within(dialog).getByText('Approve this merchant?')).toBeInTheDocument();
    expect(approveMerchant).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(approveMerchant).toHaveBeenCalledWith('m-1'));
    expect(await screen.findByText(/merchant approved/i)).toBeInTheDocument();
    expect(await within(screen.getByRole('region', { name: 'Merchant details', hidden: true })).findByText('Approved')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Suspend' })).toBeInTheDocument(); // after the dialog has closed
    expect(button('Approve')).not.toBeInTheDocument();
    expect(button('Reject')).not.toBeInTheDocument();
  });

  it('cancelling the approve confirmation sends nothing', async () => {
    getMerchant.mockResolvedValue(detail());
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });
    await user.click(button('Approve'));
    const dialog = await dialogOf();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(approveMerchant).not.toHaveBeenCalled();
  });

  it('reinstate needs a confirmation and moves a SUSPENDED merchant back to APPROVED', async () => {
    getMerchant.mockResolvedValueOnce(detail({ status: 'SUSPENDED', statusReason: REASON }))
      .mockResolvedValue(detail({ status: 'APPROVED' }));
    reinstateMerchant.mockResolvedValue(detail({ status: 'APPROVED' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Reinstate'));
    const dialog = await dialogOf();
    expect(within(dialog).getByText('Reinstate this merchant?')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Reinstate' }));

    await waitFor(() => expect(reinstateMerchant).toHaveBeenCalledWith('m-1'));
    expect(await screen.findByText(/merchant reinstated/i)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Suspend' })).toBeInTheDocument();
  });

  it('INVALID_STATUS_TRANSITION on approve (another admin acted first) reloads the merchant and says so', async () => {
    getMerchant.mockResolvedValueOnce(detail()).mockResolvedValue(detail({ status: 'REJECTED', statusReason: REASON }));
    approveMerchant.mockRejectedValue(new ApiError({ status: 409, code: 'INVALID_STATUS_TRANSITION', detail: 'cannot' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Approve'));
    await user.click(within(await dialogOf()).getByRole('button', { name: 'Approve' }));

    expect(await screen.findByText(/status changed in the meantime/i)).toBeInTheDocument();
    await waitFor(() => expect(getMerchant.mock.calls.length).toBeGreaterThanOrEqual(2));
    expect(await within(screen.getByRole('region', { name: 'Merchant details', hidden: true })).findByText('Rejected')).toBeInTheDocument();
    expect(button('Reject')).not.toBeInTheDocument(); // REJECTED offers approve only
  });

  it('another error on approve is shown as an alert and nothing changes', async () => {
    getMerchant.mockResolvedValue(detail());
    approveMerchant.mockRejectedValue(new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something broke on our side' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Approve'));
    await user.click(within(await dialogOf()).getByRole('button', { name: 'Approve' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Something broke on our side');
    expect(button('Approve')).toBeInTheDocument();
  });

  // ------------------------------------------------------------ reject and suspend (reason 10-300)

  it('reject: the reason is required, 10-300 characters (trimmed), with a counter, and nothing is sent until valid', async () => {
    getMerchant.mockResolvedValue(detail());
    rejectMerchant.mockResolvedValue(detail({ status: 'REJECTED', statusReason: REASON }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Reject'));
    const dialog = await dialogOf();
    const reason = () => within(dialog).getByLabelText('Reason');
    const submit = () => within(dialog).getByRole('button', { name: 'Reject merchant' });
    expect(within(dialog).getByText(/0 \/ 300/)).toBeInTheDocument();

    await user.click(submit());
    expect(await within(dialog).findByText('A reason is required')).toBeInTheDocument();

    await user.type(reason(), 'too short');
    expect(within(dialog).getByText(/9 \/ 300/)).toBeInTheDocument();
    await user.click(submit());
    expect(await within(dialog).findByText('The reason must be at least 10 characters')).toBeInTheDocument();

    fireEvent.change(reason(), { target: { value: 'x'.repeat(301) } });
    await user.click(submit());
    expect(await within(dialog).findByText('The reason must not exceed 300 characters')).toBeInTheDocument();
    fireEvent.change(reason(), { target: { value: `   ${'y'.repeat(9)}   ` } }); // 9 after trimming
    await user.click(submit());
    expect(await within(dialog).findByText('The reason must be at least 10 characters')).toBeInTheDocument();
    expect(rejectMerchant).not.toHaveBeenCalled();

    fireEvent.change(reason(), { target: { value: `  ${REASON}  ` } });
    await user.click(submit());
    await waitFor(() => expect(rejectMerchant).toHaveBeenCalledWith('m-1', REASON));
    expect(await screen.findByText('Merchant rejected.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('reject accepts exactly 10 and exactly 300 characters', async () => {
    getMerchant.mockResolvedValue(detail());
    rejectMerchant.mockResolvedValue(detail({ status: 'REJECTED' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Reject'));
    const dialog = await dialogOf();
    fireEvent.change(within(dialog).getByLabelText('Reason'), { target: { value: 'z'.repeat(300) } });
    await user.click(within(dialog).getByRole('button', { name: 'Reject merchant' }));
    await waitFor(() => expect(rejectMerchant).toHaveBeenCalledWith('m-1', 'z'.repeat(300)));
  });

  it('suspend explains the consequences and sends the reason', async () => {
    getMerchant.mockResolvedValueOnce(detail({ status: 'APPROVED' })).mockResolvedValue(detail({ status: 'SUSPENDED', statusReason: REASON }));
    suspendMerchant.mockResolvedValue(detail({ status: 'SUSPENDED', statusReason: REASON }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Suspend'));
    const dialog = await dialogOf();
    expect(within(dialog).getByText(/cannot log in, get api tokens or create orders.*existing parcels continue/i)).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Reason'), REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Suspend merchant' }));

    await waitFor(() => expect(suspendMerchant).toHaveBeenCalledWith('m-1', REASON));
    expect(await screen.findByText('Merchant suspended.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Reinstate' })).toBeInTheDocument();
  });

  it('keeps the typed reason and shows the error inside the dialog when the request fails', async () => {
    getMerchant.mockResolvedValue(detail());
    rejectMerchant.mockRejectedValue(new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something broke on our side' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Reject'));
    const dialog = await dialogOf();
    await user.type(within(dialog).getByLabelText('Reason'), REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Reject merchant' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Something broke on our side');
    expect(within(dialog).getByLabelText('Reason')).toHaveValue(REASON);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('shows a server field error for the reason next to the field', async () => {
    getMerchant.mockResolvedValue(detail());
    rejectMerchant.mockRejectedValue(new ApiError({
      status: 400, code: 'VALIDATION_ERROR', detail: 'Request validation failed.',
      errors: [{ field: 'reason', message: 'Reason must be between 10 and 300 characters' }],
    }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });
    await user.click(button('Reject'));
    const dialog = await dialogOf();
    await user.type(within(dialog).getByLabelText('Reason'), REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Reject merchant' }));
    expect(await within(dialog).findByText('Reason must be between 10 and 300 characters')).toBeInTheDocument();
  });

  it('INVALID_STATUS_TRANSITION on reject closes the dialog, reloads the merchant and warns', async () => {
    getMerchant.mockResolvedValueOnce(detail()).mockResolvedValue(detail({ status: 'APPROVED' }));
    rejectMerchant.mockRejectedValue(new ApiError({ status: 409, code: 'INVALID_STATUS_TRANSITION', detail: 'cannot' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });
    await user.click(button('Reject'));
    const dialog = await dialogOf();
    await user.type(within(dialog).getByLabelText('Reason'), REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Reject merchant' }));

    expect(await screen.findByText(/status changed in the meantime/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByRole('button', { name: 'Suspend' })).toBeInTheDocument();
  });

  it('cancelling the reason dialog sends nothing and the next opening starts empty', async () => {
    getMerchant.mockResolvedValue(detail());
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });
    await user.click(button('Reject'));
    let dialog = await dialogOf();
    await user.type(within(dialog).getByLabelText('Reason'), REASON);
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(rejectMerchant).not.toHaveBeenCalled();

    await user.click(button('Reject'));
    dialog = await dialogOf();
    expect(within(dialog).getByLabelText('Reason')).toHaveValue('');
  });

  // ------------------------------------------------------------ discount

  it('edits the discount: prefilled with the current value, sent as a number, shown formatted', async () => {
    getMerchant.mockResolvedValueOnce(detail({ status: 'APPROVED', discountPercent: 12.5 }))
      .mockResolvedValue(detail({ status: 'APPROVED', discountPercent: 20 }));
    setDiscount.mockResolvedValue(detail({ status: 'APPROVED', discountPercent: 20 }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });

    await user.click(button('Edit discount'));
    const dialog = await dialogOf();
    const input = within(dialog).getByLabelText('Discount (%)');
    expect(input).toHaveValue('12.5');
    await user.clear(input);
    await user.type(input, '20');
    await user.click(within(dialog).getByRole('button', { name: 'Save discount' }));

    await waitFor(() => expect(setDiscount).toHaveBeenCalledWith('m-1', 20));
    expect(await screen.findByText('Discount updated to 20.00%.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(within(screen.getByRole('region', { name: 'Merchant details' })).getByText('20.00%')).toBeInTheDocument();
  });

  it.each([
    ['', 'Enter a discount between 0 and 50'],
    ['abc', 'Use a number with at most two decimals'],
    ['-1', 'Use a number with at most two decimals'],
    ['12.345', 'Use a number with at most two decimals'],
    ['50.01', 'The discount cannot be more than 50'],
    ['51', 'The discount cannot be more than 50'],
    ['100', 'Use a number with at most two decimals'],
  ])('discount "%s" is refused with "%s" and nothing is sent', async (value, message) => {
    getMerchant.mockResolvedValue(detail({ status: 'APPROVED' }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });
    await user.click(button('Edit discount'));
    const dialog = await dialogOf();
    const input = within(dialog).getByLabelText('Discount (%)');
    await user.clear(input);
    if (value) await user.type(input, value);
    await user.click(within(dialog).getByRole('button', { name: 'Save discount' }));

    expect(await within(dialog).findByText(new RegExp(message))).toBeInTheDocument();
    expect(setDiscount).not.toHaveBeenCalled();
  });

  it.each([['0', 0], ['0.00', 0], ['50', 50], ['50.00', 50], ['25.5', 25.5], ['7.25', 7.25]])(
    'discount "%s" is accepted and sent as %s',
    async (value, sent) => {
      getMerchant.mockResolvedValue(detail({ status: 'APPROVED' }));
      setDiscount.mockResolvedValue(detail({ status: 'APPROVED', discountPercent: sent }));
      const user = userEvent.setup();
      renderDetail();
      await screen.findByRole('heading', { name: 'Lanka Crafts' });
      await user.click(button('Edit discount'));
      const dialog = await dialogOf();
      const input = within(dialog).getByLabelText('Discount (%)');
      await user.clear(input);
      await user.type(input, value);
      await user.click(within(dialog).getByRole('button', { name: 'Save discount' }));
      await waitFor(() => expect(setDiscount).toHaveBeenCalledWith('m-1', sent));
    }
  );

  it('keeps the discount input and shows the server error when saving fails', async () => {
    getMerchant.mockResolvedValue(detail({ status: 'APPROVED' }));
    setDiscount.mockRejectedValue(new ApiError({
      status: 400, code: 'VALIDATION_ERROR', detail: 'Request validation failed.',
      errors: [{ field: 'discountPercent', message: 'Discount percent must be at most 50' }],
    }));
    const user = userEvent.setup();
    renderDetail();
    await screen.findByRole('heading', { name: 'Lanka Crafts' });
    await user.click(button('Edit discount'));
    const dialog = await dialogOf();
    const input = within(dialog).getByLabelText('Discount (%)');
    await user.clear(input);
    await user.type(input, '30');
    await user.click(within(dialog).getByRole('button', { name: 'Save discount' }));

    expect(await within(dialog).findByText('Discount percent must be at most 50')).toBeInTheDocument();
    expect(input).toHaveValue('30');
  });
});

describe('TST-040 Member 3: pending merchants page and dashboard card (FR-AUTH-017, FR-AUTH-024)', { timeout: 20000 }, () => {
  beforeEach(() => {
    listMerchants.mockReset();
    pendingCount.mockReset();
  });

  const renderInRouter = (ui, url = '/') => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[url]}>{ui}</MemoryRouter>
      </QueryClientProvider>
    );
  };

  it('the pending page lists only PENDING merchants and has no status filter', async () => {
    listMerchants.mockResolvedValue({
      items: [{ id: 'm-1', businessName: 'Lanka Crafts', city: 'Colombo', status: 'PENDING', discountPercent: 0, createdAt: '2026-11-10T04:30:00Z' }],
      page: 0, size: 20, totalItems: 1, totalPages: 1,
    });
    renderInRouter(<PendingMerchantsPage />, '/admin/merchants/pending');

    expect(await screen.findByRole('heading', { name: 'Pending merchants' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Lanka Crafts' })).toHaveAttribute('href', '/admin/merchants/m-1');
    expect(listMerchants).toHaveBeenCalledWith({ status: 'PENDING', q: undefined, page: 0, size: 20 });
    expect(screen.queryByRole('combobox', { name: 'Status' })).not.toBeInTheDocument();
  });

  it('the card shows the number of pending merchants and links to the pending view', async () => {
    pendingCount.mockResolvedValue(4);
    renderInRouter(<PendingMerchantsCard />);
    expect(screen.getByText('Loading...')).toBeInTheDocument();

    expect(await screen.findByText('4')).toBeInTheDocument();
    expect(screen.getByText('merchants waiting for approval')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Review pending merchants' })).toHaveAttribute('href', '/admin/merchants?status=PENDING');
    expect(screen.getByRole('region', { name: 'Pending merchants' })).toBeInTheDocument();
  });

  it('the card handles one and zero pending merchants', async () => {
    pendingCount.mockResolvedValueOnce(1);
    const { unmount } = renderInRouter(<PendingMerchantsCard />);
    expect(await screen.findByText('merchant waiting for approval')).toBeInTheDocument();
    unmount();

    pendingCount.mockResolvedValueOnce(0);
    renderInRouter(<PendingMerchantsCard />);
    expect(await screen.findByText('No merchants are waiting for approval')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View merchants' })).toHaveAttribute('href', '/admin/merchants?status=PENDING');
  });

  it('the card has its own error state with a retry (a failure never blanks the other dashboard cards)', async () => {
    pendingCount.mockRejectedValueOnce(new ApiError({ status: 503, code: 'SERVICE_UNAVAILABLE', detail: 'Try later' }));
    pendingCount.mockResolvedValueOnce(2);
    const user = userEvent.setup();
    renderInRouter(<PendingMerchantsCard />);

    expect(await screen.findByText('Try later')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByText('2')).toBeInTheDocument();
  });

  it('is exported by name and as default, as the admin dashboard expects (master 7.4)', () => {
    expect(PendingMerchantsCard).toBe(NamedCard);
  });
});
