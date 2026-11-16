import '@testing-library/jest-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../../../../theme/theme';
import * as pricingApi from '../../../../api/adminPricing';
import AdminZonesPage from '../index.jsx';
import { createZoneSchema, renameZoneSchema } from '../ZoneDialogs.jsx';

// Form-heavy MUI pages re-render a lot; the default 5 s is too tight when the whole suite runs in parallel.
vi.setConfig({ testTimeout: 20000 });

vi.mock('../../../../api/adminPricing');

const zones = () => [
  { id: 'z-w', code: 'WESTERN', name: 'Western', active: true,
    districts: [{ code: 'COLOMBO', name: 'Colombo' }, { code: 'GAMPAHA', name: 'Gampaha' }] },
  { id: 'z-c', code: 'CENTRAL', name: 'Central', active: true, districts: [{ code: 'KANDY', name: 'Kandy' }] },
  { id: 'z-e', code: 'EMPTY_ONE', name: 'Empty One', active: true, districts: [] },
  { id: 'z-x', code: 'RETIRED', name: 'Retired', active: false, districts: [] },
];

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <AdminZonesPage />
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

describe('TST-040 Member #5 zone form rules (FR-PRC-006)', () => {
  it('zone code is [A-Z_]{2,20} and name is 2-60 characters', () => {
    expect(createZoneSchema.safeParse({ code: 'EAST_COAST', name: 'East Coast' }).success).toBe(true);
    for (const code of ['east', 'E', 'A'.repeat(21), 'EAST1', 'EAST COAST', '']) {
      expect(createZoneSchema.safeParse({ code, name: 'East' }).success).toBe(false);
    }
    expect(createZoneSchema.safeParse({ code: 'EAST', name: 'E' }).success).toBe(false);
    expect(createZoneSchema.safeParse({ code: 'EAST', name: 'x'.repeat(61) }).success).toBe(false);
    expect(renameZoneSchema.safeParse({ name: 'ok' }).success).toBe(true);
  });
});

describe('TST-040 Member #5 admin zones page (FR-PRC-006)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    pricingApi.listZones.mockResolvedValue(zones());
  });

  it('lists zones with their districts and active state', async () => {
    renderPage();
    const western = await screen.findByRole('region', { name: 'Zone Western' });
    expect(within(western).getByText('Colombo')).toBeInTheDocument();
    expect(within(western).getByText('Gampaha')).toBeInTheDocument();
    expect(within(western).getByText('Active')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Zone Retired' })).getByText('Inactive')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Zone Empty One' })).getByText('No districts')).toBeInTheDocument();
  });

  it('create: validates the code, then sends code and name', async () => {
    pricingApi.createZone.mockResolvedValue({ id: 'z-n' });
    renderPage();
    await screen.findByRole('region', { name: 'Zone Western' });
    fireEvent.click(screen.getByRole('button', { name: 'Create zone' }));
    const dialog = await screen.findByRole('dialog');

    fireEvent.change(within(dialog).getByLabelText(/Code/), { target: { value: 'east' } });
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'East Coast' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create zone' }));
    expect(await within(dialog).findByText('Use 2 to 20 capital letters or underscores')).toBeInTheDocument();
    expect(pricingApi.createZone).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText(/Code/), { target: { value: 'EAST_COAST' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create zone' }));
    await waitFor(() => expect(pricingApi.createZone).toHaveBeenCalledWith({ code: 'EAST_COAST', name: 'East Coast' }));
    expect(await screen.findByText('The zone was created.')).toBeInTheDocument();
  });

  it('create: a duplicate code from the server is shown on the field and the input is kept', async () => {
    pricingApi.createZone.mockRejectedValue({
      status: 400, code: 'VALIDATION_ERROR', detail: 'Request validation failed',
      errors: [{ field: 'code', message: 'A zone with this code already exists' }],
    });
    renderPage();
    await screen.findByRole('region', { name: 'Zone Western' });
    fireEvent.click(screen.getByRole('button', { name: 'Create zone' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/Code/), { target: { value: 'WESTERN' } });
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'Another' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create zone' }));
    expect(await within(dialog).findByText('A zone with this code already exists')).toBeInTheDocument();
    expect(within(dialog).getByLabelText(/Name/)).toHaveValue('Another');
  });

  it('rename: sends only the name', async () => {
    pricingApi.updateZone.mockResolvedValue({});
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Rename Western' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText(/Name/)).toHaveValue('Western');
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'Western Province' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save name' }));
    await waitFor(() => expect(pricingApi.updateZone).toHaveBeenCalledWith('z-w', { name: 'Western Province' }));
  });

  it('deactivate: needs a confirmation, then sends active=false', async () => {
    pricingApi.updateZone.mockResolvedValue({});
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Deactivate Empty One' }));
    const confirm = await screen.findByRole('dialog', { name: 'Deactivate zone?' });
    expect(pricingApi.updateZone).not.toHaveBeenCalled();
    fireEvent.click(within(confirm).getByRole('button', { name: 'Deactivate zone' }));
    await waitFor(() => expect(pricingApi.updateZone).toHaveBeenCalledWith('z-e', { active: false }));
  });

  it('deactivate: ZONE_IN_USE shows a clear message', async () => {
    pricingApi.updateZone.mockRejectedValue({ status: 409, code: 'ZONE_IN_USE', detail: 'x' });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Deactivate Western' }));
    const confirm = await screen.findByRole('dialog', { name: 'Deactivate zone?' });
    fireEvent.click(within(confirm).getByRole('button', { name: 'Deactivate zone' }));
    expect(await screen.findByText(/still has districts\. Move them to another zone/)).toBeInTheDocument();
  });

  it('activate: an inactive zone is activated directly', async () => {
    pricingApi.updateZone.mockResolvedValue({});
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Activate Retired' }));
    await waitFor(() => expect(pricingApi.updateZone).toHaveBeenCalledWith('z-x', { active: true }));
  });

  it('move district: offers only other active zones and sends the district code and zone id', async () => {
    pricingApi.moveDistrict.mockResolvedValue({});
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Move Kandy' }));
    const dialog = await screen.findByRole('dialog');
    const options = within(dialog).getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['Western', 'Empty One']); // not Central (current) and not Retired (inactive)
    fireEvent.change(within(dialog).getByLabelText('New zone'), { target: { value: 'z-e' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Move district' }));
    await waitFor(() => expect(pricingApi.moveDistrict).toHaveBeenCalledWith('KANDY', 'z-e'));
    expect(await screen.findByText('The district was moved.')).toBeInTheDocument();
  });

  it('shows the server error with a retry button when zones cannot be loaded', async () => {
    pricingApi.listZones.mockRejectedValueOnce({ detail: 'Pricing service is down' });
    renderPage();
    expect(await screen.findByText('Pricing service is down')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(await screen.findByRole('region', { name: 'Zone Western' })).toBeInTheDocument();
  });

  it('shows an empty state when there are no zones', async () => {
    pricingApi.listZones.mockResolvedValue([]);
    renderPage();
    expect(await screen.findByText('No zones')).toBeInTheDocument();
  });
});
