import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/http', () => ({ apiRequest: vi.fn() }));

import { apiRequest } from '../api/http';
import {
  registerMerchant,
  listCredentials,
  createCredential,
  revokeCredential,
} from '../api/merchantAccount';
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

describe('TST-040 Member 3 API modules: merchantAccount.js (FR-AUTH-016, FR-AUTH-018)', () => {
  beforeEach(() => {
    apiRequest.mockReset();
    apiRequest.mockResolvedValue({ ok: true });
  });

  it('registerMerchant posts the body to /auth/register-merchant', async () => {
    const body = { businessName: 'Lanka Crafts', email: 'shop@example.com' };
    await registerMerchant(body);
    expect(apiRequest).toHaveBeenCalledWith('POST', '/api/v1/auth/register-merchant', { body });
  });

  it('listCredentials sends only the paging parameters that are set', async () => {
    await listCredentials();
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/merchant/credentials', { query: {} });
    await listCredentials({ page: 1, size: 5 });
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/merchant/credentials', { query: { page: 1, size: 5 } });
  });

  it('createCredential posts { label }', async () => {
    await createCredential('production');
    expect(apiRequest).toHaveBeenCalledWith('POST', '/api/v1/merchant/credentials', { body: { label: 'production' } });
  });

  it('revokeCredential deletes by id and encodes it', async () => {
    await revokeCredential('3f2a');
    expect(apiRequest).toHaveBeenCalledWith('DELETE', '/api/v1/merchant/credentials/3f2a');
    await revokeCredential('a/b');
    expect(apiRequest).toHaveBeenLastCalledWith('DELETE', '/api/v1/merchant/credentials/a%2Fb');
  });

  it('passes the server response through (the secret is only in the create response)', async () => {
    apiRequest.mockResolvedValue({ id: 'c1', clientId: 'sdc_x', clientSecret: 'sds_y' });
    await expect(createCredential('one')).resolves.toEqual({ id: 'c1', clientId: 'sdc_x', clientSecret: 'sds_y' });
  });
});

describe('TST-040 Member 3 API modules: adminMerchants.js (FR-AUTH-017, FR-AUTH-024)', () => {
  beforeEach(() => {
    apiRequest.mockReset();
    apiRequest.mockResolvedValue({ ok: true });
  });

  it('listMerchants builds the query from the filters and leaves empty ones out', async () => {
    await listMerchants({});
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/admin/merchants', { query: {} });
    await listMerchants({ status: 'PENDING', q: 'alp', page: 2, size: 10, sort: 'businessName,asc' });
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/admin/merchants', {
      query: { status: 'PENDING', q: 'alp', page: 2, size: 10, sort: 'businessName,asc' },
    });
    await listMerchants({ status: '', q: '', page: 0 });
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/admin/merchants', { query: { page: 0 } });
  });

  it('getMerchant, approve and reinstate call the right paths', async () => {
    await getMerchant('m1');
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/admin/merchants/m1');
    await approveMerchant('m1');
    expect(apiRequest).toHaveBeenLastCalledWith('POST', '/api/v1/admin/merchants/m1/approve');
    await reinstateMerchant('m1');
    expect(apiRequest).toHaveBeenLastCalledWith('POST', '/api/v1/admin/merchants/m1/reinstate');
  });

  it('reject and suspend send the reason', async () => {
    await rejectMerchant('m1', 'Documents could not be verified');
    expect(apiRequest).toHaveBeenLastCalledWith('POST', '/api/v1/admin/merchants/m1/reject', {
      body: { reason: 'Documents could not be verified' },
    });
    await suspendMerchant('m1', 'Repeated policy violations');
    expect(apiRequest).toHaveBeenLastCalledWith('POST', '/api/v1/admin/merchants/m1/suspend', {
      body: { reason: 'Repeated policy violations' },
    });
  });

  it('setDiscount patches { discountPercent } as a number', async () => {
    await setDiscount('m1', 12.5);
    expect(apiRequest).toHaveBeenLastCalledWith('PATCH', '/api/v1/admin/merchants/m1/discount', {
      body: { discountPercent: 12.5 },
    });
  });

  it('pendingCount returns the number from { pendingMerchants }', async () => {
    apiRequest.mockResolvedValue({ pendingMerchants: 4 });
    await expect(pendingCount()).resolves.toBe(4);
    expect(apiRequest).toHaveBeenLastCalledWith('GET', '/api/v1/admin/dashboard/merchants');
    apiRequest.mockResolvedValue({});
    await expect(pendingCount()).resolves.toBe(0);
  });
});
