import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
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

import { listMerchants } from '../api/adminMerchants';
import { ApiError } from '../api/http';
import AdminMerchantsPage from '../pages/admin/merchants/index.jsx';
import MerchantDirectory from '../pages/admin/merchants/MerchantDirectory.jsx';
import { MerchantStatusChip } from '../pages/admin/merchants/MerchantStatusChip.jsx';

const merchant = (overrides = {}) => ({
  id: 'm-1',
  businessName: 'Lanka Crafts',
  city: 'Colombo',
  status: 'PENDING',
  discountPercent: 0,
  createdAt: '2026-11-10T04:30:00Z',
  ...overrides,
});

const pageOf = (items, extra = {}) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });

function LocationProbe() {
  const { search } = useLocation();
  return <div data-testid="search">{search}</div>;
}

function renderDirectory(url = '/admin/merchants', element = <AdminMerchantsPage />) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>
        {element}
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

const lastCall = () => listMerchants.mock.calls[listMerchants.mock.calls.length - 1][0];

describe('TST-040 Member 3: admin merchant directory (FR-AUTH-017, UC-A05)', { timeout: 20000 }, () => {
  beforeEach(() => {
    listMerchants.mockReset();
  });

  it('shows a loading state, then rows with a link to the merchant, city, status text, discount and registration date', async () => {
    let resolve;
    listMerchants.mockImplementation(() => new Promise((r) => { resolve = r; }));
    renderDirectory();
    expect(screen.getByText('Loading merchants...')).toBeInTheDocument();

    resolve(pageOf([
      merchant({ discountPercent: 12.5, status: 'APPROVED' }),
      merchant({ id: 'm-2', businessName: 'Beta Stores', city: null, status: 'REJECTED' }),
    ]));
    const table = await screen.findByRole('table', { name: 'Merchants' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);

    const link = within(rows[1]).getByRole('link', { name: 'Lanka Crafts' });
    expect(link).toHaveAttribute('href', '/admin/merchants/m-1');
    expect(within(rows[1]).getByText('Colombo')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Approved')).toBeInTheDocument();
    expect(within(rows[1]).getByText('12.50%')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Nov 10, 2026')).toBeInTheDocument();
    expect(within(rows[2]).getByText('—')).toBeInTheDocument(); // no city
    expect(within(rows[2]).getByText('0.00%')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('2 merchants');
    expect(listMerchants).toHaveBeenCalledWith({ status: undefined, q: undefined, page: 0, size: 20 });
  });

  it('first request uses the default page 0 and size 20, newest first from the server', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    renderDirectory();
    await screen.findByText('Lanka Crafts');
    expect(listMerchants).toHaveBeenCalledTimes(1);
    expect(lastCall()).toEqual({ status: undefined, q: undefined, page: 0, size: 20 });
  });

  it.each([
    ['PENDING', 'Pending', 'HourglassTopOutlinedIcon'],
    ['APPROVED', 'Approved', 'CheckCircleOutlineIcon'],
    ['REJECTED', 'Rejected', 'HighlightOffOutlinedIcon'],
    ['SUSPENDED', 'Suspended', 'PauseCircleOutlineIcon'],
  ])('the status chip for %s shows the text "%s" and an icon (never colour alone)', (status, label, iconTestId) => {
    const { container } = render(<MerchantStatusChip status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector(`[data-testid="${iconTestId}"]`)).toBeInTheDocument();
  });

  it('the status chip handles an unknown or missing status without crashing', () => {
    render(<><MerchantStatusChip status="MYSTERY" /><MerchantStatusChip /></>);
    expect(screen.getByText('MYSTERY')).toBeInTheDocument();
    expect(screen.getByText('Unknown')).toBeInTheDocument();
  });

  it('shows an empty state when no merchant exists, and an error state with retry', async () => {
    listMerchants.mockResolvedValueOnce(pageOf([]));
    const { unmount } = renderDirectory();
    expect(await screen.findByText('No merchants yet')).toBeInTheDocument();
    unmount();

    listMerchants.mockReset();
    listMerchants.mockRejectedValueOnce(new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something broke' }));
    listMerchants.mockResolvedValueOnce(pageOf([merchant()]));
    const user = userEvent.setup();
    renderDirectory();
    expect(await screen.findByText('Could not load merchants')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByText('Lanka Crafts')).toBeInTheDocument();
  });

  it('filters by status: the request and the URL change and the page goes back to 0', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    const user = userEvent.setup();
    renderDirectory('/admin/merchants?page=2');
    await screen.findByText('Lanka Crafts');
    expect(lastCall().page).toBe(2);

    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    await user.click(await screen.findByRole('option', { name: 'Pending' }));

    await waitFor(() => expect(lastCall()).toEqual({ status: 'PENDING', q: undefined, page: 0, size: 20 }));
    expect(screen.getByTestId('search')).toHaveTextContent('?status=PENDING');
    expect(screen.getByTestId('search')).not.toHaveTextContent('page=');
  });

  it('opens with the filters of the URL (the dashboard link /admin/merchants?status=PENDING)', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()], { totalItems: 45, totalPages: 3 }));
    renderDirectory('/admin/merchants?status=PENDING&q=alp&page=1&size=10');

    await screen.findByText('Lanka Crafts');
    expect(lastCall()).toEqual({ status: 'PENDING', q: 'alp', page: 1, size: 10 });
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Pending');
    expect(screen.getByLabelText('Search by business name')).toHaveValue('alp');
    expect(screen.getByRole('status')).toHaveTextContent('45 merchants match your filters');
  });

  it('ignores an invalid status, a negative page and an unsupported page size in the URL', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    renderDirectory('/admin/merchants?status=FOO&page=-3&size=7');
    await screen.findByText('Lanka Crafts');
    expect(lastCall()).toEqual({ status: undefined, q: undefined, page: 0, size: 20 });
  });

  it('searches by business-name prefix after the user stops typing: one request, back to page 0', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    const user = userEvent.setup();
    renderDirectory('/admin/merchants?page=3');
    await screen.findByText('Lanka Crafts');
    const callsBefore = listMerchants.mock.calls.length;

    await user.type(screen.getByLabelText('Search by business name'), 'alp');

    await waitFor(() => expect(lastCall()).toEqual({ status: undefined, q: 'alp', page: 0, size: 20 }));
    expect(listMerchants.mock.calls.length).toBe(callsBefore + 1); // not one request per keystroke
    expect(listMerchants.mock.calls.some(([args]) => args.q === 'a' || args.q === 'al')).toBe(false);
    expect(screen.getByTestId('search')).toHaveTextContent('?q=alp');
  });

  it('shows "no merchants match" with a Clear filters action that removes the filters', async () => {
    listMerchants.mockResolvedValue(pageOf([]));
    const user = userEvent.setup();
    renderDirectory('/admin/merchants?status=SUSPENDED&q=zzz');

    expect(await screen.findByText('No merchants match your filters')).toBeInTheDocument();
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    await user.click(screen.getByRole('button', { name: 'Clear filters' }));

    await waitFor(() => expect(lastCall()).toEqual({ status: undefined, q: undefined, page: 0, size: 20 }));
    expect(screen.getByLabelText('Search by business name')).toHaveValue('');
    expect(screen.getByTestId('search')).toHaveTextContent(/^$/);
    expect(await screen.findByText('Lanka Crafts')).toBeInTheDocument();
  });

  it('paginates with the server: next page and rows per page', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()], { totalItems: 45, totalPages: 3 }));
    const user = userEvent.setup();
    renderDirectory();
    await screen.findByText('Lanka Crafts');
    expect(screen.getByText(/1–20 of 45/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => expect(lastCall()).toEqual({ status: undefined, q: undefined, page: 1, size: 20 }));
    expect(screen.getByTestId('search')).toHaveTextContent('?page=1');

    await user.click(screen.getByRole('combobox', { name: /rows per page/i }));
    await user.click(await screen.findByRole('option', { name: '10' }));
    await waitFor(() => expect(lastCall()).toEqual({ status: undefined, q: undefined, page: 0, size: 10 }));
  });

  it('a locked status (the pending-merchants page) hides the status filter and ignores the URL status', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    renderDirectory(
      '/admin/merchants/pending?status=APPROVED',
      <MerchantDirectory lockedStatus="PENDING" title="Pending merchants" />
    );

    expect(await screen.findByRole('heading', { name: 'Pending merchants' })).toBeInTheDocument();
    expect(lastCall().status).toBe('PENDING');
    expect(screen.queryByRole('combobox', { name: 'Status' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Search by business name')).toBeInTheDocument();
  });

  it('the pending page has its own empty message', async () => {
    listMerchants.mockResolvedValue(pageOf([]));
    renderDirectory('/admin/merchants/pending', <MerchantDirectory lockedStatus="PENDING" title="Pending merchants" />);
    expect(await screen.findByText('No merchants are waiting for approval')).toBeInTheDocument();
  });

  it('has labelled controls and a polite live region for the result count', async () => {
    listMerchants.mockResolvedValue(pageOf([merchant()]));
    renderDirectory();
    await screen.findByText('Lanka Crafts');
    expect(screen.getByRole('search', { name: 'Merchant filters' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Status' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByRole('status')).toHaveTextContent('1 merchant');
  });
});
