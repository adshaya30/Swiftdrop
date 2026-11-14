import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Dashboard, { DashboardCounts } from '../dashboard';
import List from '../parcels/list';
import { getDashboardSummary, listParcels } from '../../../api/customerParcels';
import { formatDateTime } from '../../../utils/format';

vi.mock('../../../api/customerParcels', () => ({ getDashboardSummary: vi.fn(), listParcels: vi.fn() }));
const parcel = (id = 'one', status = 'CREATED') => ({ id, trackingNumber: `SD-${id}`, status, pickup: { city: 'Colombo', districtName: 'Colombo' }, dropoff: { city: 'Kandy', districtName: 'Kandy' }, fee: { total: '650.00' }, createdAt: '2026-11-14T20:00:00Z' });
const page = (items = [parcel()], totalPages = 1) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages });
const mount = (Component) => render(<MemoryRouter><Component /></MemoryRouter>);
const deferred = () => { let resolve; let reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
beforeEach(() => { vi.resetAllMocks(); getDashboardSummary.mockRejectedValue({ status: 404 }); listParcels.mockResolvedValue(page()); });
afterEach(cleanup);

describe('TST-040 customer dashboard', () => {
  it('shows loading then the five-parcel fallback without estimating total counts', async () => {
    const pending = deferred();
    listParcels.mockReturnValue(pending.promise);
    mount(Dashboard);
    expect(screen.getByText('Loading your dashboard…')).toBeInTheDocument();
    await waitFor(() => expect(listParcels).toHaveBeenCalledWith({ page: 0, size: 5, sort: 'createdAt,desc' }));
    await act(async () => pending.resolve(page(Array.from({ length: 6 }, (_, index) => parcel(String(index))))));
    expect(screen.getAllByRole('article')).toHaveLength(5);
    expect(screen.getAllByText('Unavailable')).toHaveLength(2);
    expect(screen.getByRole('link', { name: 'SD-0' })).toHaveAttribute('href', '/customer/parcels/0');
    expect(screen.queryByRole('link', { name: 'SD-5' })).not.toBeInTheDocument();
  });
  it('provides accessible quick-action links', async () => {
    mount(Dashboard);
    await screen.findByText('Recent parcels');
    expect(screen.getByRole('link', { name: 'New Delivery' })).toHaveAttribute('href', '/customer/parcels/new');
    expect(screen.getByRole('link', { name: 'Track Parcel' })).toHaveAttribute('href', '/track');
    expect(screen.getByRole('link', { name: 'My Parcels' })).toHaveAttribute('href', '/customer/parcels');
  });
  it('handles empty fallback data', async () => {
    listParcels.mockResolvedValue(page([], 0));
    mount(Dashboard);
    await screen.findByText('No deliveries yet');
    expect(screen.getAllByText('Unavailable')).toHaveLength(2);
  });
  it('does not guess the shape of an undocumented summary or request it', async () => {
    getDashboardSummary.mockResolvedValue({ arbitrary: 999 });
    mount(Dashboard);
    await screen.findByText('Recent parcels');
    expect(screen.queryByText('999')).not.toBeInTheDocument();
    expect(screen.getAllByText('Unavailable')).toHaveLength(2);
    expect(getDashboardSummary).not.toHaveBeenCalled();
  });
  it('renders only valid authoritative count display values, including zero', () => {
    render(<DashboardCounts active={0} delivered={123} />);
    expect(screen.getByText('0')).toBeInTheDocument();
    expect(screen.getByText('123')).toBeInTheDocument();
    cleanup();
    render(<DashboardCounts active={-1} delivered={NaN} />);
    expect(screen.getAllByText('Unavailable')).toHaveLength(2);
  });
  it('retries a failed fallback request', async () => {
    listParcels.mockRejectedValueOnce(new Error('offline'));
    mount(Dashboard);
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    await screen.findByRole('link', { name: 'SD-one' });
    expect(listParcels).toHaveBeenCalledTimes(2);
  });
  it('is independent of the optional summary service while its contract is pending', async () => {
    getDashboardSummary.mockRejectedValue({ status: 503 });
    mount(Dashboard);
    await screen.findByText('Recent parcels');
    expect(getDashboardSummary).not.toHaveBeenCalled();
    expect(listParcels).toHaveBeenCalledTimes(1);
  });
  it.each([401, 403])('handles authorization failure %s without showing parcels', async (status) => {
    listParcels.mockRejectedValue({ status });
    mount(Dashboard);
    await screen.findByRole('alert');
    expect(listParcels).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('article')).not.toBeInTheDocument();
  });
});

describe('TST-040 my parcels', () => {
  it('requests zero-based defaults and renders server data with shared formatters', async () => {
    mount(List);
    await screen.findByRole('link', { name: 'SD-one' });
    expect(listParcels).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'createdAt,desc' });
    expect(within(screen.getByRole('article')).getByText('Created', { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText(formatDateTime(parcel().createdAt))).toBeInTheDocument();
    expect(screen.getByText('LKR 650.00')).toBeInTheDocument();
    expect(screen.getByText('Colombo, Colombo')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
  });
  it('shows loading and empty states', async () => {
    const pending = deferred(); listParcels.mockReturnValue(pending.promise);
    mount(List);
    expect(screen.getByText('Loading your parcels…')).toBeInTheDocument();
    await act(async () => pending.resolve(page([], 0)));
    expect(screen.getByText('No parcels found')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Page 0 of 0');
  });
  it('applies search, status, inclusive dates and approved sort parameters', async () => {
    mount(List); await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.change(screen.getByRole('textbox', { name: 'Tracking-number prefix' }), { target: { value: 'SD-AB' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Status' }), { target: { value: 'DELIVERED' } });
    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-11-01' } });
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-11-14' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Sort by' }), { target: { value: 'trackingNumber' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Sort direction' }), { target: { value: 'asc' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Parcels per page' }), { target: { value: '100' } });
    expect(listParcels).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(listParcels).toHaveBeenLastCalledWith({ q: 'SD-AB', status: 'DELIVERED', from: '2026-11-01', to: '2026-11-14', page: 0, size: 100, sort: 'trackingNumber,asc' }));
    expect([...screen.getByRole('combobox', { name: 'Parcels per page' }).options].map(option => Number(option.value))).toEqual([20, 50, 100]);
  });
  it('paginates on the server and resets page when filters change', async () => {
    listParcels.mockResolvedValue(page([parcel()], 3));
    mount(List); await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await waitFor(() => expect(listParcels).toHaveBeenLastCalledWith({ page: 1, size: 20, sort: 'createdAt,desc' }));
    await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    await waitFor(() => expect(listParcels).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'createdAt,desc' }));
    await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.change(screen.getByRole('combobox', { name: 'Sort by' }), { target: { value: 'status' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(listParcels).toHaveBeenLastCalledWith({ page: 0, size: 20, sort: 'status,desc' }));
  });
  it('rejects reversed date ranges without making a request', async () => {
    mount(List); await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.change(screen.getByLabelText('From date'), { target: { value: '2026-11-15' } });
    fireEvent.change(screen.getByLabelText('To date'), { target: { value: '2026-11-14' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(screen.getByRole('alert')).toHaveTextContent('From date');
    expect(listParcels).toHaveBeenCalledTimes(1);
  });
  it('resets all filters and retries failures without showing raw errors', async () => {
    listParcels.mockRejectedValueOnce({ status: 500, detail: 'PRIVATE SERVER BODY' });
    mount(List); await screen.findByRole('alert');
    expect(screen.queryByText('PRIVATE SERVER BODY')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.change(screen.getByRole('textbox', { name: 'Tracking-number prefix' }), { target: { value: 'SD-AB' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reset filters' }));
    expect(screen.getByRole('textbox', { name: 'Tracking-number prefix' })).toHaveValue('');
    await waitFor(() => expect(listParcels).toHaveBeenCalledTimes(3));
  });
  it.each([401, 403])('clears old parcel data on authorization failure %s', async (status) => {
    listParcels.mockResolvedValueOnce(page()).mockRejectedValueOnce({ status });
    mount(List); await screen.findByRole('link', { name: 'SD-one' });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await screen.findByRole('alert');
    expect(screen.queryByRole('link', { name: 'SD-one' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(status === 401 ? 'sign in again' : 'permission');
  });
  it('ignores stale responses from a previous filter request', async () => {
    const older = deferred();
    listParcels.mockReturnValueOnce(older.promise).mockResolvedValueOnce(page([parcel('new')]));
    mount(List);
    await waitFor(() => expect(listParcels).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await screen.findByRole('link', { name: 'SD-new' });
    await act(async () => older.resolve(page([parcel('old')])));
    expect(screen.queryByRole('link', { name: 'SD-old' })).not.toBeInTheDocument();
  });
  it('supports keyboard entry and form submission', async () => {
    const user = userEvent.setup(); mount(List);
    await screen.findByRole('link', { name: 'SD-one' });
    await user.tab(); // New Delivery link
    await user.tab(); // Search input
    expect(screen.getByRole('textbox', { name: 'Tracking-number prefix' })).toHaveFocus();
    await user.keyboard('SD-KEY{Enter}');
    await waitFor(() => expect(listParcels).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'SD-KEY' })));
  });
});
