import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../api/merchantAccount', () => ({
  registerMerchant: vi.fn(),
  listCredentials: vi.fn(),
  createCredential: vi.fn(),
  revokeCredential: vi.fn(),
}));

import { listCredentials, createCredential, revokeCredential } from '../api/merchantAccount';
import { ApiError } from '../api/http';
import MerchantCredentialsPage from '../pages/merchant/credentials/index.jsx';

const ACTIVE = {
  id: 'c-1',
  clientId: 'sdc_AAAAAAAAAAAAAAAAAAAAAAAA',
  label: 'Production store',
  status: 'ACTIVE',
  createdAt: '2026-11-10T04:30:00Z', // 10:00 AM in Asia/Colombo
  lastUsedAt: '2026-11-12T08:00:00Z',
};
const REVOKED = {
  id: 'c-2',
  clientId: 'sdc_BBBBBBBBBBBBBBBBBBBBBBBB',
  label: 'Old test key',
  status: 'REVOKED',
  createdAt: '2026-10-01T04:30:00Z',
  lastUsedAt: null,
};
const SECOND_ACTIVE = { ...ACTIVE, id: 'c-3', clientId: 'sdc_CCCCCCCCCCCCCCCCCCCCCCCC', label: 'Staging', lastUsedAt: null };
const CREATED = {
  id: 'c-9',
  clientId: 'sdc_NEWNEWNEWNEWNEWNEWNEWNEWN'.slice(0, 28),
  clientSecret: 'sds_SECRETSECRETSECRETSECRETSECRETSECRET123',
  label: 'Production store',
  status: 'ACTIVE',
  createdAt: '2026-11-14T05:00:00Z',
};

const pageOf = (items, extra = {}) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const utils = render(
    <QueryClientProvider client={client}>
      <MerchantCredentialsPage />
    </QueryClientProvider>
  );
  return { client, ...utils };
}

const openCreateDialog = async (user) => {
  const header = screen.getAllByRole('button', { name: 'Create credential' })[0];
  await user.click(header);
  return screen.findByRole('dialog');
};

describe('TST-040 Member 3: API credentials page (FR-AUTH-018, UC-M03)', { timeout: 20000 }, () => {
  beforeEach(() => {
    listCredentials.mockReset();
    createCredential.mockReset();
    revokeCredential.mockReset();
    localStorage.clear();
    sessionStorage.clear();
  });

  // ------------------------------------------------------------ list states

  it('shows a loading state, then the credentials with status text, Asia/Colombo dates and "Never" for unused', async () => {
    let resolve;
    listCredentials.mockImplementation(() => new Promise((r) => { resolve = r; }));
    renderPage();
    expect(screen.getByText('Loading your API credentials...')).toBeInTheDocument();

    resolve(pageOf([ACTIVE, REVOKED]));
    const table = await screen.findByRole('table', { name: 'API credentials' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3); // header + 2

    expect(within(rows[1]).getByText('Production store')).toBeInTheDocument();
    expect(within(rows[1]).getByText(ACTIVE.clientId)).toBeInTheDocument();
    expect(within(rows[1]).getByText('Active')).toBeInTheDocument();
    // 04:30 UTC is 10:00 AM and 08:00 UTC is 1:30 PM in Asia/Colombo (UTC+05:30), 12-hour clock
    expect(rows[1]).toHaveTextContent(/Nov 10, 2026, 10:00\s?AM/);
    expect(rows[1]).toHaveTextContent(/Nov 12, 2026, 1:30\s?PM/);
    expect(within(rows[2]).getByText('Revoked')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Never')).toBeInTheDocument();
    expect(table).not.toHaveTextContent(/sds_|secret|hash/i);
  });

  it('shows an empty state with a create action when there are no credentials', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText('No API credentials yet')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: 'Create credential' }).pop());
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('shows an error state with a retry that loads the list again', async () => {
    listCredentials.mockRejectedValueOnce(new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something broke' }));
    listCredentials.mockResolvedValueOnce(pageOf([ACTIVE]));
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Could not load your credentials')).toBeInTheDocument();
    expect(screen.getByText('Something broke')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByText('Production store')).toBeInTheDocument();
    expect(listCredentials).toHaveBeenCalledTimes(2);
  });

  it('paginates: the next page is requested from the server', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE], { totalItems: 45, totalPages: 3 }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Production store');
    expect(listCredentials).toHaveBeenLastCalledWith({ page: 0, size: 20 });

    await user.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => expect(listCredentials).toHaveBeenLastCalledWith({ page: 1, size: 20 }));
  });

  // ------------------------------------------------------------ create and the one-time secret

  it('creates a credential, shows the client ID and secret once with a warning, and refreshes the list', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    createCredential.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('No API credentials yet');

    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText('Label'), '  Production store  ');
    await user.click(within(dialog).getByRole('button', { name: 'Create credential' }));

    await waitFor(() => expect(createCredential).toHaveBeenCalledWith('Production store'));
    expect(await within(dialog).findByText(/copy your client secret now/i)).toBeInTheDocument();
    expect(within(dialog).getByText(/you will not see it again/i)).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Client ID')).toHaveValue(CREATED.clientId);
    expect(within(dialog).getByLabelText('Client secret')).toHaveValue(CREATED.clientSecret);
    expect(within(dialog).getByLabelText('Client secret')).toHaveAttribute('readonly');
    await waitFor(() => expect(listCredentials.mock.calls.length).toBeGreaterThanOrEqual(2)); // list refreshed
  });

  it('copies the client secret and the client ID to the clipboard', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    createCredential.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('No API credentials yet');
    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText('Label'), 'Production store');
    await user.click(within(dialog).getByRole('button', { name: 'Create credential' }));
    await within(dialog).findByLabelText('Client secret');

    await user.click(within(dialog).getByRole('button', { name: 'Copy client secret' }));
    expect(await navigator.clipboard.readText()).toBe(CREATED.clientSecret);
    expect(await within(dialog).findByText(/copied to the clipboard/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: 'Copy client ID' }));
    expect(await navigator.clipboard.readText()).toBe(CREATED.clientId);
  });

  it('cannot be dismissed with Escape while the secret is shown; "I have saved the secret" clears it for good', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    createCredential.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('No API credentials yet');
    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText('Label'), 'Production store');
    await user.click(within(dialog).getByRole('button', { name: 'Create credential' }));
    await within(dialog).findByLabelText('Client secret');

    await user.keyboard('{Escape}');
    expect(screen.getByDisplayValue(CREATED.clientSecret)).toBeInTheDocument(); // still there

    await user.click(within(dialog).getByRole('button', { name: 'I have saved the secret' }));
    await waitFor(() => expect(screen.queryByDisplayValue(CREATED.clientSecret)).not.toBeInTheDocument());
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // opening the dialog again shows an empty form, never the old secret
    const again = await openCreateDialog(user);
    expect(within(again).getByLabelText('Label')).toHaveValue('');
    expect(screen.queryByDisplayValue(CREATED.clientSecret)).not.toBeInTheDocument();
  });

  it('never stores the secret in browser storage, cookies or the TanStack caches', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    createCredential.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    const { client } = renderPage();
    await screen.findByText('No API credentials yet');
    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText('Label'), 'Production store');
    await user.click(within(dialog).getByRole('button', { name: 'Create credential' }));
    await within(dialog).findByLabelText('Client secret');

    const dump = JSON.stringify({
      local: Object.entries(localStorage),
      session: Object.entries(sessionStorage),
      cookies: document.cookie,
      queries: client.getQueryCache().getAll().map((q) => q.state.data),
      mutations: client.getMutationCache().getAll().map((m) => m.state),
    });
    expect(dump).not.toContain(CREATED.clientSecret);
  });

  it('validates the label: required, at most 50 characters, trimmed', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    createCredential.mockResolvedValue(CREATED);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('No API credentials yet');
    const dialog = await openCreateDialog(user);
    const submit = () => within(dialog).getByRole('button', { name: 'Create credential' });

    await user.click(submit());
    expect(await within(dialog).findByText('Label is required')).toBeInTheDocument();
    await user.type(within(dialog).getByLabelText('Label'), '   ');
    await user.click(submit());
    expect(await within(dialog).findByText('Label is required')).toBeInTheDocument();

    fireEvent.change(within(dialog).getByLabelText('Label'), { target: { value: 'x'.repeat(51) } });
    await user.click(submit());
    expect(await within(dialog).findByText('Label must not exceed 50 characters')).toBeInTheDocument();
    expect(createCredential).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText('Label'), { target: { value: 'y'.repeat(50) } });
    await user.click(submit());
    await waitFor(() => expect(createCredential).toHaveBeenCalledWith('y'.repeat(50)));
  });

  it('keeps the label and shows an alert when creating fails, and disables the buttons while it runs', async () => {
    listCredentials.mockResolvedValue(pageOf([]));
    let reject;
    createCredential.mockImplementation(() => new Promise((_, r) => { reject = r; }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('No API credentials yet');
    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText('Label'), 'Production store');
    await user.click(within(dialog).getByRole('button', { name: 'Create credential' }));

    expect(await within(dialog).findByRole('button', { name: 'Creating...' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    reject(new ApiError({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something broke on our side' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Something broke on our side');
    expect(within(dialog).getByLabelText('Label')).toHaveValue('Production store');
    expect(within(dialog).queryByLabelText('Client secret')).not.toBeInTheDocument();
  });

  // ------------------------------------------------------------ limit of 2 active credentials

  it('disables "Create credential" and explains the limit when two credentials are active', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE, SECOND_ACTIVE, REVOKED]));
    renderPage();
    await screen.findByRole('table', { name: 'API credentials' });

    const button = screen.getByRole('button', { name: 'Create credential' });
    expect(button).toBeDisabled();
    expect(screen.getByText(/you have 2 active credentials, which is the maximum/i)).toBeInTheDocument();
  });

  it('a revoked credential does not count towards the limit', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE, REVOKED]));
    renderPage();
    await screen.findByRole('table', { name: 'API credentials' });
    expect(screen.getByRole('button', { name: 'Create credential' })).toBeEnabled();
    expect(screen.queryByText(/which is the maximum/i)).not.toBeInTheDocument();
  });

  it('handles CREDENTIAL_LIMIT_REACHED from the server with an explanation and a refreshed list', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE]));
    createCredential.mockRejectedValue(new ApiError({ status: 409, code: 'CREDENTIAL_LIMIT_REACHED', detail: 'At most 2' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Production store');

    const dialog = await openCreateDialog(user);
    await user.type(within(dialog).getByLabelText('Label'), 'Third key');
    await user.click(within(dialog).getByRole('button', { name: 'Create credential' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent(/already have 2 active credentials.*revoke one first/i);
    expect(within(dialog).getByLabelText('Label')).toHaveValue('Third key');
    await waitFor(() => expect(listCredentials.mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  // ------------------------------------------------------------ revoke

  it('offers Revoke only for ACTIVE credentials', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE, REVOKED]));
    renderPage();
    await screen.findByRole('table', { name: 'API credentials' });
    expect(screen.getAllByRole('button', { name: /^Revoke credential / })).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Revoke credential Production store' })).toBeInTheDocument();
  });

  it('revoking needs a confirmation: nothing is sent until the user confirms, Cancel changes nothing', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE]));
    revokeCredential.mockResolvedValue(null);
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Production store');

    await user.click(screen.getByRole('button', { name: 'Revoke credential Production store' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Revoke this credential?')).toBeInTheDocument();
    expect(within(dialog).getByText(/will stop working immediately/i)).toBeInTheDocument();
    expect(revokeCredential).not.toHaveBeenCalled();

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(revokeCredential).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Revoke credential Production store' }));
    const again = await screen.findByRole('dialog');
    listCredentials.mockResolvedValue(pageOf([{ ...ACTIVE, status: 'REVOKED' }]));
    await user.click(within(again).getByRole('button', { name: 'Revoke credential' }));

    await waitFor(() => expect(revokeCredential).toHaveBeenCalledWith('c-1'));
    expect(await screen.findByText('Revoked')).toBeInTheDocument(); // list refreshed
    expect(screen.queryByRole('button', { name: /^Revoke credential / })).not.toBeInTheDocument();
  });

  it('shows an error when revoking fails and still refreshes the list', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE]));
    revokeCredential.mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', detail: 'Credential not found.' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Production store');

    await user.click(screen.getByRole('button', { name: 'Revoke credential Production store' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Revoke credential' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Credential not found.');
    await waitFor(() => expect(listCredentials.mock.calls.length).toBeGreaterThanOrEqual(2));
  });

  // ------------------------------------------------------------ what the page must not offer

  it('explains the API-only order flow and the token endpoint, and offers no rotate, copy-later or create-order action', async () => {
    listCredentials.mockResolvedValue(pageOf([ACTIVE]));
    renderPage();
    await screen.findByText('Production store');

    expect(screen.getByRole('heading', { name: 'Using your credentials' })).toBeInTheDocument();
    expect(screen.getByText('POST /api/v1/oauth/token')).toBeInTheDocument();
    expect(screen.getByLabelText('Example token request')).toHaveTextContent('grant_type=client_credentials');
    expect(screen.getByLabelText('Example token request')).toHaveTextContent('orders:write');
    expect(screen.queryByText(/rotate/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /rotate|show secret|view secret/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /create order|new order/i })).not.toBeInTheDocument();
  });
});
