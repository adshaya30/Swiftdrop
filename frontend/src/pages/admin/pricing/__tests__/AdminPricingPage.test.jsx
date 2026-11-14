import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as pricingApi from '../../../../api/adminPricing';
import AdminPricingPage from '../index.jsx';
import { toInputValue, validateFee } from '../feeValidation.js';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../api/adminPricing');

const zones = [
  { id: 'z-w', code: 'WESTERN', name: 'Western', active: true, districts: [] },
  { id: 'z-c', code: 'CENTRAL', name: 'Central', active: true, districts: [] },
];
// Western -> Central is configured, the other three pairs are not.
const matrix = [
  { originZoneId: 'z-w', originZoneCode: 'WESTERN', destinationZoneId: 'z-c', destinationZoneCode: 'CENTRAL',
    baseFee: 500, perKgFee: 150 },
];
const sizes = [
  { size: 'LARGE', surcharge: 400, description: 'up to 60 x 40 x 40 cm' },
  { size: 'SMALL', surcharge: 0, description: 'up to 20 x 15 x 10 cm' },
  { size: 'MEDIUM', surcharge: 150, description: 'up to 40 x 30 x 20 cm' },
];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <AdminPricingPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 Member #5 pricing form validation (FR-PRC-007)', () => {
  it('accepts amounts of 0 to 1,000,000.00 with at most 2 decimals', () => {
    for (const ok of ['0', '0.00', '500', '500.5', '999999.99', '1000000', '1000000.00']) {
      expect(validateFee(ok)).toBeNull();
    }
  });

  it('rejects empty, negative, 3-decimal, non-numeric and too large amounts', () => {
    for (const bad of ['', '  ', '-1', '-0.01', '1.001', 'abc', '1e3', '1,000', '1000000.01', '1000001']) {
      expect(validateFee(bad)).not.toBeNull();
    }
  });

  it('shows server amounts with two decimals', () => {
    expect(toInputValue(500)).toBe('500.00');
    expect(toInputValue('150.5')).toBe('150.50');
    expect(toInputValue(null)).toBe('');
  });
});

describe('TST-040 Member #5 admin pricing page (FR-PRC-007)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    pricingApi.listZones.mockResolvedValue(zones);
    pricingApi.getPricingMatrix.mockResolvedValue(matrix);
    pricingApi.getSizeSurcharges.mockResolvedValue(sizes);
  });

  it('shows every zone pair with two decimals and marks the pairs that are not configured', async () => {
    renderPage();
    expect(await screen.findByLabelText('Base fee Western to Central')).toHaveValue('500.00');
    expect(screen.getByLabelText('Per-kg fee Western to Central')).toHaveValue('150.00');
    expect(screen.getByLabelText('Base fee Central to Western')).toHaveValue('');
    expect(screen.getAllByText('Not configured')).toHaveLength(3);
    expect(screen.getAllByRole('row')).toHaveLength(5); // header + 2 x 2 pairs
  });

  it('keeps Save disabled until a value changes, and blocks invalid fees', async () => {
    renderPage();
    const base = await screen.findByLabelText('Base fee Western to Central');
    const save = screen.getByRole('button', { name: 'Save Western to Central' });
    expect(save).toBeDisabled();

    fireEvent.change(base, { target: { value: '-5' } });
    expect(await screen.findByText(/at most 2 decimals/)).toBeInTheDocument();
    expect(save).toBeDisabled();
    fireEvent.change(base, { target: { value: '12.345' } });
    expect(save).toBeDisabled();
    fireEvent.change(base, { target: { value: '1000000.01' } });
    expect(await screen.findByText('The maximum is 1,000,000.00')).toBeInTheDocument();
    expect(save).toBeDisabled();

    fireEvent.change(base, { target: { value: '600' } });
    expect(save).toBeEnabled();
  });

  it('asks for confirmation, then sends both fees for the pair and shows a success message', async () => {
    pricingApi.setPricing.mockResolvedValue({});
    renderPage();
    fireEvent.change(await screen.findByLabelText('Base fee Western to Central'), { target: { value: '600' } });
    fireEvent.change(screen.getByLabelText('Per-kg fee Western to Central'), { target: { value: '200.5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Western to Central' }));

    const dialog = await screen.findByRole('dialog', { name: 'Confirm pricing change' });
    expect(within(dialog).getByText(/LKR 500\.00 base/)).toBeInTheDocument();
    expect(within(dialog).getByText(/LKR 600\.00 base and LKR 200\.50 per kg/)).toBeInTheDocument();
    expect(within(dialog).getByText(/future quotes and parcels only/)).toBeInTheDocument();
    expect(pricingApi.setPricing).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole('button', { name: 'Save pricing' }));
    await waitFor(() => expect(pricingApi.setPricing).toHaveBeenCalledWith('z-w', 'z-c', { baseFee: 600, perKgFee: 200.5 }));
    expect(await screen.findByText('Pricing for Western to Central was saved.')).toBeInTheDocument();
  });

  it('cancelling the confirmation sends nothing and keeps the edited values', async () => {
    renderPage();
    fireEvent.change(await screen.findByLabelText('Base fee Western to Central'), { target: { value: '600' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Western to Central' }));
    const dialog = await screen.findByRole('dialog', { name: 'Confirm pricing change' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(pricingApi.setPricing).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Base fee Western to Central')).toHaveValue('600');
  });

  it('creates a pair that is not configured yet', async () => {
    pricingApi.setPricing.mockResolvedValue({});
    renderPage();
    fireEvent.change(await screen.findByLabelText('Base fee Central to Western'), { target: { value: '450' } });
    expect(screen.getByRole('button', { name: 'Save Central to Western' })).toBeDisabled(); // per-kg still empty
    fireEvent.change(screen.getByLabelText('Per-kg fee Central to Western'), { target: { value: '120' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Central to Western' }));
    const dialog = await screen.findByRole('dialog', { name: 'Confirm pricing change' });
    expect(within(dialog).getByText(/not configured before/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save pricing' }));
    await waitFor(() => expect(pricingApi.setPricing).toHaveBeenCalledWith('z-c', 'z-w', { baseFee: 450, perKgFee: 120 }));
  });

  it('shows a server error and keeps the input when saving fails', async () => {
    pricingApi.setPricing.mockRejectedValue({ status: 400, detail: 'Request validation failed',
      errors: [{ field: 'baseFee', message: 'must be less than or equal to 1000000.00' }] });
    renderPage();
    fireEvent.change(await screen.findByLabelText('Base fee Western to Central'), { target: { value: '600' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save Western to Central' }));
    const dialog = await screen.findByRole('dialog', { name: 'Confirm pricing change' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save pricing' }));
    expect(await screen.findByText('must be less than or equal to 1000000.00')).toBeInTheDocument();
    expect(screen.getByLabelText('Base fee Western to Central')).toHaveValue('600');
  });

  it('shows the three size surcharges in order and saves them as a list after confirmation', async () => {
    pricingApi.setSizeSurcharges.mockResolvedValue(sizes);
    renderPage();
    expect(await screen.findByLabelText('Small surcharge')).toHaveValue('0.00');
    expect(screen.getByLabelText('Medium surcharge')).toHaveValue('150.00');
    expect(screen.getByLabelText('Large surcharge')).toHaveValue('400.00');
    const save = screen.getByRole('button', { name: 'Save surcharges' });
    expect(save).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Large surcharge'), { target: { value: 'x' } });
    expect(save).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Large surcharge'), { target: { value: '450' } });
    fireEvent.click(save);

    const dialog = await screen.findByRole('dialog', { name: 'Confirm pricing change' });
    expect(within(dialog).getByText(/Large: LKR 400\.00 to LKR 450\.00/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save pricing' }));
    await waitFor(() => expect(pricingApi.setSizeSurcharges).toHaveBeenCalledWith([
      { size: 'SMALL', surcharge: 0 }, { size: 'MEDIUM', surcharge: 150 }, { size: 'LARGE', surcharge: 450 },
    ]));
  });

  it('shows an error with retry when the matrix cannot be loaded, without hiding the surcharges', async () => {
    pricingApi.getPricingMatrix.mockRejectedValueOnce({ detail: 'Pricing service is down' });
    renderPage();
    expect(await screen.findByText('Pricing service is down')).toBeInTheDocument();
    expect(await screen.findByLabelText('Small surcharge')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByLabelText('Base fee Western to Central')).toBeInTheDocument();
  });
});
