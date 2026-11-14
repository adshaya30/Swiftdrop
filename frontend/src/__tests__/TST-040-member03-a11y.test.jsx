import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('../api/merchantAccount', () => ({
  registerMerchant: vi.fn(),
  listCredentials: vi.fn(),
  createCredential: vi.fn(),
  revokeCredential: vi.fn(),
}));
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

import { registerMerchant, listCredentials, createCredential } from '../api/merchantAccount';
import { listMerchants, getMerchant, rejectMerchant } from '../api/adminMerchants';
import RegisterMerchantPage from '../pages/merchant/register/index.jsx';
import MerchantCredentialsPage from '../pages/merchant/credentials/index.jsx';
import AdminMerchantsPage from '../pages/admin/merchants/index.jsx';
import MerchantDetailPage from '../pages/admin/merchants/detail/index.jsx';

const client = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const ACTIVE = {
  id: 'c-1', clientId: 'sdc_AAAAAAAAAAAAAAAAAAAAAAAA', label: 'Production store', status: 'ACTIVE',
  createdAt: '2026-11-10T04:30:00Z', lastUsedAt: null,
};
const MERCHANT = {
  id: 'm-1', userId: 'u-1', businessName: 'Lanka Crafts', businessRegistrationNo: null, contactName: 'Nimal Perera',
  email: 'shop@example.com', phone: '+94771234567', addressLine1: '12 Galle Road', city: 'Colombo', status: 'PENDING',
  statusReason: null, discountPercent: 0, decidedAt: null, decidedBy: null, createdAt: '2026-11-10T04:30:00Z',
};

describe('TST-040 Member 3: keyboard use, labels and focus (WCAG 2.1 AA basics, master prompt section 9)', { timeout: 20000 }, () => {
  beforeEach(() => {
    [registerMerchant, listCredentials, createCredential, listMerchants, getMerchant, rejectMerchant]
      .forEach((mock) => mock.mockReset());
  });

  // ------------------------------------------------------------ registration form

  it('every field of the registration form has an accessible name and a visible label', () => {
    render(<MemoryRouter><RegisterMerchantPage /></MemoryRouter>);
    const inputs = screen.getAllByRole('textbox');
    expect(inputs.length).toBeGreaterThanOrEqual(7);
    inputs.forEach((input) => {
      expect(input).toHaveAccessibleName();
      expect(document.querySelector(`label[for="${input.id}"]`)).toBeTruthy(); // a visible label, not only a placeholder
    });
    expect(screen.getByLabelText('Password')).toHaveAccessibleName('Password');
  });

  it('the registration form can be completed and submitted with the keyboard only, in reading order', async () => {
    const user = userEvent.setup();
    registerMerchant.mockResolvedValue({ message: 'pending' });
    render(<MemoryRouter><RegisterMerchantPage /></MemoryRouter>);

    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'businessName');
    await user.keyboard('Lanka Crafts');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'businessRegistrationNo');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'contactName');
    await user.keyboard('Nimal Perera');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'phone');
    await user.keyboard('0771234567');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'email');
    await user.keyboard('shop@example.com');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'password');
    await user.keyboard('Str0ng!Pass#1');
    await user.tab();
    expect(document.activeElement).toHaveAccessibleName('Show password');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'addressLine1');
    await user.keyboard('12 Galle Road');
    await user.tab();
    expect(document.activeElement).toHaveAttribute('id', 'city');
    await user.keyboard('Colombo{Enter}'); // Enter submits the form

    await waitFor(() => expect(registerMerchant).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('Your registration is waiting for admin approval')).toBeInTheDocument();
  });

  it('a failed submit moves focus to the first invalid field and links each error to its field', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><RegisterMerchantPage /></MemoryRouter>);
    await user.click(screen.getByRole('button', { name: /register business/i }));

    await waitFor(() => expect(document.activeElement).toHaveAttribute('id', 'businessName'));
    const name = screen.getByLabelText('Business name');
    expect(name).toHaveAttribute('aria-invalid', 'true');
    const describedBy = name.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy)).toHaveTextContent('Business name is required');
    expect(registerMerchant).not.toHaveBeenCalled();
  });

  // ------------------------------------------------------------ credentials

  it('credentials: the table has column headers, the create dialog is named and focuses its field, Escape returns focus', async () => {
    listCredentials.mockResolvedValue({ items: [ACTIVE], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={client()}>
        <MemoryRouter><MerchantCredentialsPage /></MemoryRouter>
      </QueryClientProvider>
    );
    const table = await screen.findByRole('table', { name: 'API credentials' });
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent))
      .toEqual(['Label', 'Client ID', 'Status', 'Created', 'Last used', 'Actions']);

    const trigger = screen.getByRole('button', { name: 'Create credential' });
    await user.click(trigger);
    const dialog = await screen.findByRole('dialog', { name: 'Create API credential' });
    await waitFor(() => expect(within(dialog).getByLabelText('Label')).toHaveFocus());

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('credentials: the one-time secret view is reachable by keyboard (copy buttons, then the confirm button)', async () => {
    listCredentials.mockResolvedValue({ items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 });
    createCredential.mockResolvedValue({
      id: 'c-9', clientId: 'sdc_NEWNEWNEWNEWNEWNEWNEWNEWN', clientSecret: 'sds_SECRET', label: 'Production store', status: 'ACTIVE', createdAt: '2026-11-14T05:00:00Z',
    });
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={client()}>
        <MemoryRouter><MerchantCredentialsPage /></MemoryRouter>
      </QueryClientProvider>
    );
    await screen.findByText('No API credentials yet');
    await user.click(screen.getAllByRole('button', { name: 'Create credential' })[0]);
    const dialog = await screen.findByRole('dialog', { name: 'Create API credential' });
    await user.type(within(dialog).getByLabelText('Label'), 'Production store{Enter}');

    const secretDialog = await screen.findByRole('dialog', { name: 'Save your client secret' });
    expect(secretDialog).toBeInTheDocument();
    const reachable = [];
    for (let i = 0; i < 8; i += 1) {
      await user.tab();
      const active = document.activeElement;
      if (active && secretDialog.contains(active)) reachable.push(active.getAttribute('aria-label') || active.textContent || active.id);
    }
    expect(reachable).toEqual(expect.arrayContaining(['Copy client ID', 'Copy client secret', 'I have saved the secret']));
  });

  // ------------------------------------------------------------ admin pages

  it('merchant directory: labelled filters, a search region and a table with column headers', async () => {
    listMerchants.mockResolvedValue({ items: [MERCHANT], page: 0, size: 20, totalItems: 1, totalPages: 1 });
    render(
      <QueryClientProvider client={client()}>
        <MemoryRouter initialEntries={['/admin/merchants']}><AdminMerchantsPage /></MemoryRouter>
      </QueryClientProvider>
    );
    const table = await screen.findByRole('table', { name: 'Merchants' });
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent))
      .toEqual(['Business', 'City', 'Status', 'Discount', 'Registered']);
    expect(screen.getByRole('search', { name: 'Merchant filters' })).toBeInTheDocument();
    expect(screen.getByLabelText('Search by business name')).toHaveAccessibleName();
    expect(screen.getByRole('combobox', { name: 'Status' })).toBeInTheDocument();
  });

  it('merchant review: dialogs are named by their title, focus the reason, Escape cancels, focus returns to the button', async () => {
    getMerchant.mockResolvedValue(MERCHANT);
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={client()}>
        <MemoryRouter initialEntries={['/admin/merchants/m-1']}>
          <Routes><Route path="/admin/merchants/:id" element={<MerchantDetailPage />} /></Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
    await screen.findByRole('heading', { name: 'Lanka Crafts', level: 5 }).catch(() => screen.findByText('Lanka Crafts'));

    const reject = await screen.findByRole('button', { name: 'Reject' });
    reject.focus();
    await user.keyboard('{Enter}');
    const dialog = await screen.findByRole('dialog', { name: 'Reject this merchant?' });
    await waitFor(() => expect(within(dialog).getByLabelText('Reason')).toHaveFocus());

    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(rejectMerchant).not.toHaveBeenCalled();
    await waitFor(() => expect(reject).toHaveFocus());
  });
});

// -------------------------------------------------------------------------------------------------------------------
// Source guards: the master prompt rules that are easy to break by accident (sections 9 and 10, SRS FR-UI-001, FR-UI-005).
// -------------------------------------------------------------------------------------------------------------------

const SRC = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const MY_PATHS = [
  'pages/merchant/register',
  'pages/merchant/credentials',
  'pages/admin/merchants',
  'api/merchantAccount.js',
  'api/adminMerchants.js',
];

function sourceFiles(relative) {
  const full = path.join(SRC, relative);
  if (statSync(full).isFile()) return [full];
  return readdirSync(full).flatMap((entry) => {
    const child = path.join(relative, entry);
    const stat = statSync(path.join(SRC, child));
    if (stat.isDirectory()) return entry === '__tests__' ? [] : sourceFiles(child);
    return /\.(jsx?|css)$/.test(entry) ? [path.join(SRC, child)] : [];
  });
}

describe('TST-040 Member 3: master prompt rules hold in Member 3 files', () => {
  const files = MY_PATHS.flatMap(sourceFiles);
  const scan = (pattern) =>
    files.flatMap((file) =>
      readFileSync(file, 'utf8').split('\n')
        .map((line, index) => ({ file: path.relative(SRC, file), line: index + 1, text: line }))
        .filter(({ text }) => pattern.test(text))
    );

  it('covers the files of all three pages and both API modules', () => {
    const names = files.map((f) => path.relative(SRC, f).replaceAll('\\', '/'));
    for (const expected of ['pages/merchant/register/index.jsx', 'pages/merchant/credentials/index.jsx',
      'pages/admin/merchants/index.jsx', 'pages/admin/merchants/detail/index.jsx', 'pages/admin/merchants/PendingMerchantsCard.jsx',
      'api/merchantAccount.js', 'api/adminMerchants.js']) {
      expect(names).toContain(expected);
    }
  });

  it('uses theme tokens only: no hard-coded colours (master 10)', () => {
    expect(scan(/#[0-9a-fA-F]{3,8}\b/)).toEqual([]);
  });

  it('never touches browser storage or cookies (FR-UI-001)', () => {
    expect(scan(/localStorage|sessionStorage|document\.cookie|indexedDB/)).toEqual([]);
  });

  it('never renders user text as HTML (FR-UI-005)', () => {
    expect(scan(/dangerouslySetInnerHTML|innerHTML/)).toEqual([]);
  });

  it('has no hard-coded hosts: relative URLs only (master 9)', () => {
    expect(scan(/https?:\/\/|localhost/)).toEqual([]);
  });

  it('has no credential rotation and no create-order button or label (master 10)', () => {
    const outsideComments = ({ text }) => !/^\s*(\/\/|\*|\/\*)/.test(text);
    expect(scan(/rotate/i).filter(outsideComments).filter(({ text }) => !/no rotate/i.test(text))).toEqual([]);
    // a control whose text is exactly "Create order" or "New order" (sentences that mention orders are fine)
    expect(scan(/>\s*(create|new) order\s*<|(label|title|aria-label)=["'{`]*\s*(create|new) order/i)).toEqual([]);
    expect(scan(/['"`](create|new) order['"`]/i)).toEqual([]);
  });
});
