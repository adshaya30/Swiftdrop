import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as reportsApi from '../../../../api/adminReports';
import AdminReportsPage from '../index.jsx';
import { addDays, daysInclusive, defaultRange, todayColombo, validateRange } from '../dateRange.js';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../api/adminReports');

const summary = {
  from: '2026-02-01', to: '2026-03-02', created: 120, delivered: 90, failed: 10, cancelled: 5, inProgress: 15,
  successRate: 90.0, failureRate: 10.0,
};
const daily = [
  { date: '2026-03-01', created: 4, delivered: 3, failed: 1, cancelled: 0 },
  { date: '2026-03-02', created: 0, delivered: 0, failed: 0, cancelled: 0 },
];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <AdminReportsPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 Member #5 report date rules (FR-PAR-013)', () => {
  it('works in Asia/Colombo: 18:45 UTC is already the next day', () => {
    expect(todayColombo(new Date('2026-03-01T18:29:00Z'))).toBe('2026-03-01');
    expect(todayColombo(new Date('2026-03-01T18:45:00Z'))).toBe('2026-03-02');
  });

  it('defaults to the last 30 days, inclusive of today', () => {
    const range = defaultRange(new Date('2026-03-01T18:45:00Z'));
    expect(range).toEqual({ from: '2026-02-01', to: '2026-03-02' });
    expect(daysInclusive(range.from, range.to)).toBe(30);
  });

  it('adds days across month and leap-year boundaries', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addDays('2028-03-01', -1)).toBe('2028-02-29');
  });

  it('accepts at most 366 days, requires both dates and from <= to', () => {
    expect(validateRange('2026-01-01', '2027-01-01')).toBeNull();
    expect(validateRange('2026-01-01', '2027-01-02')).toMatch(/at most 366/);
    expect(validateRange('2026-03-05', '2026-03-01')).toMatch(/must not be after/);
    expect(validateRange('', '2026-03-01')).toMatch(/both/);
    expect(validateRange('2026-03-01', '2026-03-01')).toBeNull();
  });
});

describe('TST-040 Member #5 admin reports page (FR-PAR-013)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-03-01T18:45:00Z'));
    reportsApi.getReportSummary.mockResolvedValue(summary);
    reportsApi.getDailyReport.mockResolvedValue(daily);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads the last 30 days by default and shows the summary cards and the daily table, without charts', async () => {
    const { container } = renderPage();
    expect(await screen.findByText('Success rate')).toBeInTheDocument();
    expect(reportsApi.getReportSummary).toHaveBeenCalledWith({ from: '2026-02-01', to: '2026-03-02' });
    expect(reportsApi.getDailyReport).toHaveBeenCalledWith({ from: '2026-02-01', to: '2026-03-02' });

    const cards = within(screen.getByRole('region', { name: 'Summary' }));
    for (const label of ['Created', 'Delivered', 'Failed', 'Cancelled', 'In progress', 'Success rate', 'Failure rate']) {
      expect(cards.getByText(label)).toBeInTheDocument();
    }
    expect(cards.getByText('120')).toBeInTheDocument();
    expect(cards.getByText('90.0%')).toBeInTheDocument();
    expect(cards.getByText('10.0%')).toBeInTheDocument();

    const table = within(screen.getByRole('table', { name: 'Daily report' }));
    expect(table.getAllByRole('row')).toHaveLength(3); // header + the zero-count day is kept
    expect(container.querySelector('canvas, svg[role="img"], .recharts-wrapper')).toBeNull();
  });

  it('refuses a range longer than 366 days without calling the API', async () => {
    renderPage();
    await screen.findByText('Success rate');
    reportsApi.getReportSummary.mockClear();
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2025-01-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-03-02' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText(/at most 366 days/)).toBeInTheDocument();
    expect(reportsApi.getReportSummary).not.toHaveBeenCalled();
  });

  it('refuses from after to, and sends a valid new range', async () => {
    renderPage();
    await screen.findByText('Success rate');
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-03-10' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-03-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    expect(await screen.findByText(/must not be after/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-03-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-03-02' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));
    await waitFor(() => expect(reportsApi.getReportSummary)
      .toHaveBeenLastCalledWith({ from: '2026-03-01', to: '2026-03-02' }));
    await waitFor(() => expect(reportsApi.getDailyReport)
      .toHaveBeenLastCalledWith({ from: '2026-03-01', to: '2026-03-02' }));
  });

  it('keeps the daily table when the summary fails, and offers a retry for the summary only', async () => {
    reportsApi.getReportSummary.mockRejectedValueOnce({ detail: 'Summary is unavailable' });
    renderPage();
    expect(await screen.findByText('Summary is unavailable')).toBeInTheDocument();
    expect(await screen.findByRole('table', { name: 'Daily report' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByText('Success rate')).toBeInTheDocument();
  });

  it('shows an empty state when the range has no days', async () => {
    reportsApi.getDailyReport.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No data')).toBeInTheDocument();
  });
});
