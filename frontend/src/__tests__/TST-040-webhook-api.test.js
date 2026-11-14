import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as http from '../api/http';
import webhookApi, {
  getWebhook,
  saveWebhook,
  rotateSecret,
  sendTest,
  listDeliveries,
  retryDelivery,
  getWebhookSummary,
} from '../api/webhook';

vi.mock('../api/http', () => ({
  apiRequest: vi.fn(),
}));

describe('TST-040 Webhook API Client (src/api/webhook.js)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls getWebhook endpoint', async () => {
    http.apiRequest.mockResolvedValueOnce({ url: 'https://example.com', enabled: true, hasSecret: true });
    const res = await getWebhook();
    expect(http.apiRequest).toHaveBeenCalledWith('GET', '/api/v1/merchant/webhook');
    expect(res.hasSecret).toBe(true);
  });

  it('calls saveWebhook endpoint with body', async () => {
    http.apiRequest.mockResolvedValueOnce({ url: 'https://example.com', enabled: true, hasSecret: true });
    await saveWebhook({ url: 'https://example.com', enabled: true });
    expect(http.apiRequest).toHaveBeenCalledWith('PUT', '/api/v1/merchant/webhook', {
      body: { url: 'https://example.com', enabled: true },
    });
  });

  it('calls rotateSecret endpoint', async () => {
    http.apiRequest.mockResolvedValueOnce({ secret: 'whsec_newSecret123' });
    const res = await rotateSecret();
    expect(http.apiRequest).toHaveBeenCalledWith('POST', '/api/v1/merchant/webhook/rotate-secret');
    expect(res.secret).toBe('whsec_newSecret123');
  });

  it('calls sendTest endpoint', async () => {
    http.apiRequest.mockResolvedValueOnce({ message: 'Ping queued' });
    await sendTest();
    expect(http.apiRequest).toHaveBeenCalledWith('POST', '/api/v1/merchant/webhook/test');
  });

  it('calls listDeliveries endpoint with query filters', async () => {
    http.apiRequest.mockResolvedValueOnce({ content: [], totalElements: 0 });
    await listDeliveries({ status: 'FAILED', page: 1, size: 20 });
    expect(http.apiRequest).toHaveBeenCalledWith('GET', '/api/v1/merchant/webhook/deliveries', {
      query: { status: 'FAILED', page: 1, size: 20 },
    });
  });

  it('calls retryDelivery endpoint with delivery ID', async () => {
    const deliveryId = 'd1e2f3a4-0000-0000-0000-000000000001';
    http.apiRequest.mockResolvedValueOnce({ eventId: deliveryId, status: 'PENDING' });
    await retryDelivery(deliveryId);
    expect(http.apiRequest).toHaveBeenCalledWith('POST', `/api/v1/merchant/webhook/deliveries/${deliveryId}/retry`);
  });

  it('calls getWebhookSummary endpoint', async () => {
    http.apiRequest.mockResolvedValueOnce({ failed24h: 1, pending: 2, succeeded24h: 10 });
    const res = await getWebhookSummary();
    expect(http.apiRequest).toHaveBeenCalledWith('GET', '/api/v1/merchant/webhook/summary');
    expect(res.succeeded24h).toBe(10);
  });

  it('verifies default export contains all functions', () => {
    expect(webhookApi.getWebhook).toBe(getWebhook);
    expect(webhookApi.saveWebhook).toBe(saveWebhook);
    expect(webhookApi.rotateSecret).toBe(rotateSecret);
    expect(webhookApi.sendTest).toBe(sendTest);
    expect(webhookApi.listDeliveries).toBe(listDeliveries);
    expect(webhookApi.retryDelivery).toBe(retryDelivery);
    expect(webhookApi.getWebhookSummary).toBe(getWebhookSummary);
  });
});

