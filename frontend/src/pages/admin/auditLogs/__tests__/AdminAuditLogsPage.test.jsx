import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as auditApi from '../../../../api/auditLogs';
import AdminAuditLogsPage from '../index.jsx';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../api/auditLogs');

const ACTOR = '3f2a9c1e-5b7d-4e8a-9c3b-1a2b3c4d5e6f';

const entry = (over = {}) => ({
  id: 1, service: 'pricing-service', actorUserId: ACTOR, actorRole: 'ADMIN', action: 'PRICING_UPDATED',
  entityType: 'ZONE_PRICING', entityId: 'z1:z2', details: { newBaseFee: 600, zoneCode: 'WESTERN' },
  occurredAt: '2026-03-01T18:45:00Z', ...over,
});
const pageOf = (items, extra = {}) => ({ items, page: 0, size: 20, totalItems: items.length, totalPages: 1, ...extra });

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <AdminAuditLogsPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

const lastCall = () => auditApi.listAuditLogs.mock.calls.at(-1)[0];

describe('TST-040 Member #5 admin audit log page (FR-TRK-005, AT-39)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auditApi.listAuditLogs.mockResolvedValue(pageOf([
      entry(),
      entry({ id: 2, action: 'ZONE_CREATED', actorUserId: null, actorRole: 'SYSTEM', details: null,
        entityType: 'ZONE', entityId: 'z9' }),
    ]));
  });

  it('lists entries newest first as returned, with actor, service, entity and read-only details', async () => {
    renderPage();
    const table = within(await screen.findByRole('table', { name: 'Audit entries' }));
    expect(table.getByText('PRICING_UPDATED')).toBeInTheDocument();
    expect(table.getByText('ZONE_CREATED')).toBeInTheDocument();
    expect(table.getByText(ACTOR)).toBeInTheDocument();
    expect(table.getByText('System')).toBeInTheDocument();
    expect(table.getByText('z1:z2')).toBeInTheDocument();
    expect(table.getAllByText('pricing-service')).toHaveLength(2);
    const details = table.getByLabelText('Details');
    expect(details.tagName).toBe('PRE');
    expect(details).toHaveTextContent('"newBaseFee": 600');
    expect(details).toHaveTextContent('"zoneCode": "WESTERN"');
    expect(table.getByText('—')).toBeInTheDocument(); // the entry without details
    expect(lastCall()).toMatchObject({ page: 0, size: 20 });
  });

  it('shows details as text and never as HTML', async () => {
    auditApi.listAuditLogs.mockResolvedValue(pageOf([entry({ details: { note: '<img src=x onerror=alert(1)>' } })]));
    const { container } = renderPage();
    await screen.findByRole('table', { name: 'Audit entries' });
    expect(container.querySelector('img')).toBeNull();
    expect(screen.getByLabelText('Details')).toHaveTextContent('<img src=x onerror=alert(1)>');
  });

  it('sends the action, actorId and date filters only when Apply is pressed and resets to page 0', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Audit entries' });
    const before = auditApi.listAuditLogs.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Action'), { target: { value: ' PRICING_UPDATED ' } });
    fireEvent.change(screen.getByLabelText('Actor id'), { target: { value: ACTOR } });
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-03-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-03-31' } });
    expect(auditApi.listAuditLogs.mock.calls.length).toBe(before);

    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(lastCall()).toMatchObject({
      action: 'PRICING_UPDATED', actorId: ACTOR, from: '2026-03-01', to: '2026-03-31', page: 0,
    }));
  });

  it('refuses an actor id that is not a UUID and a from date after the to date', async () => {
    renderPage();
    await screen.findByRole('table', { name: 'Audit entries' });
    const before = auditApi.listAuditLogs.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Actor id'), { target: { value: 'not-a-uuid' } });
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-03-10' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-03-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));

    expect(await screen.findByText('Enter a valid actor id (UUID)')).toBeInTheDocument();
    expect(screen.getByText(/must not be after/)).toBeInTheDocument();
    expect(auditApi.listAuditLogs.mock.calls.length).toBe(before);
  });

  it('pages through the results and clears filters', async () => {
    auditApi.listAuditLogs.mockResolvedValue(pageOf([entry()], { totalItems: 45, totalPages: 3 }));
    renderPage();
    await screen.findByRole('table', { name: 'Audit entries' });
    fireEvent.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => expect(lastCall()).toMatchObject({ page: 1, size: 20 }));

    fireEvent.change(screen.getByLabelText('Action'), { target: { value: 'ZONE_CREATED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await waitFor(() => expect(lastCall()).toMatchObject({ action: 'ZONE_CREATED', page: 0 }));
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }));
    await waitFor(() => expect(lastCall()).toMatchObject({ action: '', page: 0 }));
    expect(screen.getByLabelText('Action')).toHaveValue('');
  });

  it('shows an empty state when nothing matches', async () => {
    auditApi.listAuditLogs.mockResolvedValue(pageOf([], { totalItems: 0, totalPages: 0 }));
    renderPage();
    expect(await screen.findByText('No audit entries')).toBeInTheDocument();
  });

  it('shows the server error with a retry button', async () => {
    auditApi.listAuditLogs.mockRejectedValueOnce({ detail: 'Tracking service is down' });
    renderPage();
    expect(await screen.findByText('Tracking service is down')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByRole('table', { name: 'Audit entries' })).toBeInTheDocument();
  });
});
