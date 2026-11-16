import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import * as webhookApi from '../api/webhook';
import MerchantWebhookPage from '../pages/merchant/webhook/index.jsx';

// Mock Webhook API module
vi.mock('../api/webhook', () => ({
  getWebhook: vi.fn(),
  saveWebhook: vi.fn(),
  rotateSecret: vi.fn(),
  sendTest: vi.fn(),
  listDeliveries: vi.fn(),
  retryDelivery: vi.fn(),
  getWebhookSummary: vi.fn(),
}));

// Mock Auth API for AuthProvider
vi.mock('../api/auth', () => ({
  refresh: vi.fn().mockResolvedValue({
    accessToken: 'mock-token',
    user: { id: 'm-1', fullName: 'Merchant User', role: 'MERCHANT' },
  }),
  login: vi.fn(),
  logout: vi.fn().mockResolvedValue(),
  getAuthConfig: vi.fn().mockResolvedValue({ googleEnabled: false }),
}));

function renderWithProviders(ui) {
  return render(
    <BrowserRouter>
      <AuthProvider>{ui}</AuthProvider>
    </BrowserRouter>
  );
}

describe('TST-040 Webhook Frontend Slice (Member #10)', { timeout: 15000 }, () => {
  const mockConfigWithSecret = {
    url: 'https://merchant.example.com/api/webhook',
    enabled: true,
    hasSecret: true,
  };

  const mockDeliveries = [
    {
      eventId: 'd1e2f3a4-0000-0000-0000-000000000001',
      merchantId: 'm-1',
      eventType: 'parcel.status.changed',
      trackingNumber: 'SD-1234567890',
      status: 'SUCCEEDED',
      attemptCount: 1,
      lastHttpStatus: 200,
      lastError: null,
      createdAt: '2026-11-10T10:00:00Z',
      lastAttemptAt: '2026-11-10T10:00:01Z',
    },
    {
      eventId: 'd1e2f3a4-0000-0000-0000-000000000002',
      merchantId: 'm-1',
      eventType: 'parcel.status.changed',
      trackingNumber: 'SD-9876543210',
      status: 'FAILED',
      attemptCount: 6,
      lastHttpStatus: 500,
      lastError: 'HTTP 500 Internal Server Error',
      createdAt: '2026-11-10T11:00:00Z',
      lastAttemptAt: '2026-11-10T17:00:00Z',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    webhookApi.getWebhook.mockResolvedValue(mockConfigWithSecret);
    webhookApi.getWebhookSummary.mockResolvedValue({
      failed24h: 3,
      pending: 1,
      succeeded24h: 24,
    });
    webhookApi.listDeliveries.mockResolvedValue({
      content: mockDeliveries,
      totalElements: 2,
    });
  });

  describe('Configuration & Secret Security', () => {
    it('renders config data with masked secret and never exposes raw secret from GET', async () => {
      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('https://merchant.example.com/api/webhook')).toBeInTheDocument();
      });

      // Shows masked indicator
      expect(screen.getByText(/whsec_••••••••••••/i)).toBeInTheDocument();

      // Ensure no raw secret is rendered in DOM
      expect(screen.queryByText(/whsec_[A-Za-z0-9_-]{43}/)).not.toBeInTheDocument();
    });

    it('shows secret once upon first save in modal and clears it upon close', async () => {
      webhookApi.saveWebhook.mockResolvedValueOnce({
        url: 'https://merchant.example.com/api/webhook',
        enabled: true,
        hasSecret: true,
        secret: 'whsec_firstTimeGeneratedSecret1234567890123',
      });

      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('https://merchant.example.com/api/webhook')).toBeInTheDocument();
      });

      const saveBtn = screen.getByRole('button', { name: /Save Configuration/i });
      fireEvent.click(saveBtn);

      // Secret modal appears with the generated secret
      await waitFor(() => {
        expect(screen.getByText('Webhook Signing Secret')).toBeInTheDocument();
        expect(screen.getByText('whsec_firstTimeGeneratedSecret1234567890123')).toBeInTheDocument();
      });

      // Close modal
      const ackButton = screen.getByRole('button', { name: /I have securely saved this secret/i });
      fireEvent.click(ackButton);

      // Verify secret is cleared from the DOM immediately
      await waitFor(() => {
        expect(screen.queryByText('whsec_firstTimeGeneratedSecret1234567890123')).not.toBeInTheDocument();
      });
    });

    it('validates URL field protocol and prevents invalid submission', async () => {
      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('https://merchant.example.com/api/webhook')).toBeInTheDocument();
      });

      const urlInput = screen.getByLabelText(/Webhook Target URL/i);
      fireEvent.change(urlInput, { target: { value: 'ftp://invalid-protocol.com' } });

      const saveBtn = screen.getByRole('button', { name: /Save Configuration/i });
      fireEvent.click(saveBtn);

      await waitFor(() => {
        expect(screen.getByText(/URL must use HTTPS protocol/i)).toBeInTheDocument();
      });

      expect(webhookApi.saveWebhook).not.toHaveBeenCalled();
    });

    it('preserves user input if server returns a validation error like SSRF refusal', async () => {
      webhookApi.saveWebhook.mockRejectedValueOnce({
        detail: 'SSRF validation failed: Restricted address',
      });

      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByDisplayValue('https://merchant.example.com/api/webhook')).toBeInTheDocument();
      });

      const urlInput = screen.getByLabelText(/Webhook Target URL/i);
      fireEvent.change(urlInput, { target: { value: 'https://169.254.169.254/webhook' } });

      const saveBtn = screen.getByRole('button', { name: /Save Configuration/i });
      fireEvent.click(saveBtn);

      await waitFor(() => {
        expect(screen.getByText(/SSRF validation failed: Restricted address/i)).toBeInTheDocument();
      });

      // User's input is NOT wiped
      expect(screen.getByDisplayValue('https://169.254.169.254/webhook')).toBeInTheDocument();
    });

    it('handles rotate secret confirmation dialog and displays newly rotated secret', async () => {
      webhookApi.rotateSecret.mockResolvedValueOnce({
        secret: 'whsec_rotatedSecret9876543210987654321098765',
      });

      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Rotate Secret/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Rotate Secret/i }));

      // Confirm dialog opens
      expect(screen.getByText('Rotate Webhook Secret')).toBeInTheDocument();

      // Click Confirm in dialog
      const confirmBtn = screen.getAllByRole('button', { name: /Rotate Secret/i });
      fireEvent.click(confirmBtn[confirmBtn.length - 1]);

      // Shows new secret once
      await waitFor(() => {
        expect(screen.getByText('whsec_rotatedSecret9876543210987654321098765')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /I have securely saved this secret/i }));
      await waitFor(() => {
        expect(screen.queryByText('whsec_rotatedSecret9876543210987654321098765')).not.toBeInTheDocument();
      });
    });
  });

  describe('Summary Counters', () => {
    it('renders summary counters correctly', async () => {
      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByText('Pending Delivery')).toBeInTheDocument();
        expect(screen.getByText('1')).toBeInTheDocument();
        expect(screen.getByText('Succeeded (Last 24h)')).toBeInTheDocument();
        expect(screen.getByText('24')).toBeInTheDocument();
        expect(screen.getByText('Failed (Last 24h)')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
      });
    });

    it('shows "unavailable" counters when notification-service does not answer without breaking page', async () => {
      webhookApi.getWebhookSummary.mockRejectedValueOnce(new Error('Network error'));

      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        const unavailables = screen.getAllByText('unavailable');
        expect(unavailables.length).toBeGreaterThanOrEqual(1);
      });

      // Page structure remains functional
      expect(screen.getByText('Webhook Settings')).toBeInTheDocument();
      expect(screen.getByLabelText(/Webhook Target URL/i)).toBeInTheDocument();
    });
  });

  describe('Delivery Log & Manual Retry', () => {
    it('renders deliveries table with chips and timestamps', async () => {
      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByText('SD-1234567890')).toBeInTheDocument();
        expect(screen.getByText('SD-9876543210')).toBeInTheDocument();
      });

      expect(screen.getByText('Succeeded')).toBeInTheDocument();
      expect(screen.getByText('Failed')).toBeInTheDocument();
      expect(screen.getByText('HTTP 500 Internal Server Error')).toBeInTheDocument();
    });

    it('calls listDeliveries with status filter when filter is changed', async () => {
      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByRole('combobox', { name: /Filter Status/i })).toBeInTheDocument();
      });

      const combobox = screen.getByRole('combobox', { name: /Filter Status/i });
      fireEvent.mouseDown(combobox);

      const failedOption = await screen.findByRole('option', { name: /Failed/i });
      fireEvent.click(failedOption);

      await waitFor(() => {
        expect(webhookApi.listDeliveries).toHaveBeenCalledWith(
          expect.objectContaining({ status: 'FAILED' })
        );
      });
    });

    it('allows retry for FAILED rows with ConfirmDialog', async () => {
      webhookApi.retryDelivery.mockResolvedValueOnce({
        eventId: 'd1e2f3a4-0000-0000-0000-000000000002',
        status: 'PENDING',
      });

      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Retry/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Retry/i }));

      // Confirm dialog opens
      expect(screen.getByText(/Retry Webhook Delivery/i)).toBeInTheDocument();

      const retryConfirmBtn = screen.getByRole('button', { name: /Retry Now/i });
      fireEvent.click(retryConfirmBtn);

      await waitFor(() => {
        expect(webhookApi.retryDelivery).toHaveBeenCalledWith('d1e2f3a4-0000-0000-0000-000000000002');
      });
    });

    it('sends test ping when clicking Send Test Ping button', async () => {
      webhookApi.sendTest.mockResolvedValueOnce({
        message: 'Test webhook ping enqueued successfully.',
        eventId: 'test-event-uuid',
      });

      renderWithProviders(<MerchantWebhookPage />);

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Send Test Ping/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Send Test Ping/i }));

      await waitFor(() => {
        expect(webhookApi.sendTest).toHaveBeenCalled();
        expect(screen.getByText(/Test webhook ping enqueued successfully/i)).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility & Small Viewports (360px)', () => {
    it('renders cleanly in a 360px container without layout explosion', async () => {
      const { container } = renderWithProviders(
        <div style={{ width: 360 }}>
          <MerchantWebhookPage />
        </div>
      );

      await waitFor(() => {
        expect(screen.getByText('Webhook Settings')).toBeInTheDocument();
      });

      expect(container.querySelector('#webhook-url-input')).toBeInTheDocument();
      expect(container.querySelector('table')).toBeInTheDocument();
    });
  });
});

