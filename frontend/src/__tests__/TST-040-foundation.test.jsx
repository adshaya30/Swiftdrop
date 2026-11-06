import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../theme/theme';
import { formatDateTime, formatDate, formatMoney, applyServerErrors } from '../utils/format';
import { setAccessToken, getAccessToken, clearAccessToken, ApiError, apiRequest } from '../api/http';
import StatusChip from '../components/StatusChip';
import EmptyState from '../components/EmptyState';
import ErrorState from '../components/ErrorState';
import LoadingState from '../components/LoadingState';
import ConfirmDialog from '../components/ConfirmDialog';
import PageHeader from '../components/PageHeader';
import { routeTable, ROLE_HOMES } from '../routes/routeTable';
import LoginPage from '../pages/auth/login/index';
import RegisterPage from '../pages/auth/register/index';
import { AuthProvider } from '../context/AuthContext';

function renderWithProviders(ui) {
  return render(
    <ThemeProvider theme={theme}>
      <BrowserRouter>
        <AuthProvider>
          {ui}
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  );
}

describe('TST-040 Formatting & Utilities (Asia/Colombo & Money)', () => {
  it('formats dates in Asia/Colombo timezone', () => {
    const formatted = formatDateTime('2026-11-04T00:00:00Z');
    expect(formatted).toContain('Nov 4, 2026');
    expect(formatted).toContain('5:30 AM');
  });

  it('formats dates cleanly with formatDate', () => {
    const formatted = formatDate('2026-11-04T00:00:00Z');
    expect(formatted).toContain('Nov 4, 2026');
  });

  it('formats currency strictly as LKR without floating point rounding', () => {
    expect(formatMoney('1234.50')).toBe('LKR 1,234.50');
    expect(formatMoney(500)).toBe('LKR 500.00');
    expect(formatMoney(0)).toBe('LKR 0.00');
    expect(formatMoney(null)).toBe('LKR 0.00');
  });

  it('applies server RFC 7807 problem details to form errors', () => {
    const setError = vi.fn();
    const error = new ApiError({
      status: 400,
      code: 'VALIDATION_ERROR',
      detail: 'Invalid input',
      errors: [
        { field: 'email', message: 'Email must be valid' },
        { field: 'password', message: 'Password too short' },
      ],
    });

    applyServerErrors(setError, error);
    expect(setError).toHaveBeenCalledWith('email', { type: 'server', message: 'Email must be valid' });
    expect(setError).toHaveBeenCalledWith('password', { type: 'server', message: 'Password too short' });
  });
});

describe('TST-040 Memory Token Management (No LocalStorage)', () => {
  beforeEach(() => {
    clearAccessToken();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('holds token in memory variable only', () => {
    setAccessToken('test-jwt-token-xyz');
    expect(getAccessToken()).toBe('test-jwt-token-xyz');

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('accessToken')).toBeNull();
    expect(sessionStorage.getItem('token')).toBeNull();

    clearAccessToken();
    expect(getAccessToken()).toBeNull();
  });
});

describe('TST-040 StatusChip Labels & SRS 6.2 Compliance', () => {
  it('renders all SRS 6.2 lifecycle statuses with correct labels and icons', () => {
    const statuses = [
      { key: 'CREATED', label: 'Created' },
      { key: 'PICKED_UP', label: 'Picked Up' },
      { key: 'IN_TRANSIT', label: 'In Transit' },
      { key: 'OUT_FOR_DELIVERY', label: 'Out for Delivery' },
      { key: 'DELIVERED', label: 'Delivered' },
      { key: 'FAILED', label: 'Failed' },
      { key: 'CANCELLED', label: 'Cancelled' },
    ];

    statuses.forEach(({ key, label }) => {
      const { unmount } = render(<StatusChip status={key} />);
      expect(screen.getByText(label)).toBeInTheDocument();
      unmount();
    });
  });
});

describe('TST-040 Shared Foundation Components', () => {
  it('renders EmptyState with action button', () => {
    const onAction = vi.fn();
    render(
      <EmptyState
        title="No Parcels"
        description="You have no active shipments."
        actionLabel="Create Parcel"
        onAction={onAction}
      />
    );
    expect(screen.getByText('No Parcels')).toBeInTheDocument();
    expect(screen.getByText('You have no active shipments.')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: 'Create Parcel' });
    fireEvent.click(btn);
    expect(onAction).toHaveBeenCalledTimes(1);
  });

  it('renders ErrorState with retry button', () => {
    const onRetry = vi.fn();
    render(<ErrorState title="Network Error" message="Please check your connection" onRetry={onRetry} />);
    expect(screen.getByText('Network Error')).toBeInTheDocument();
    const retryBtn = screen.getByRole('button', { name: 'Try Again' });
    fireEvent.click(retryBtn);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('renders LoadingState with custom message', () => {
    render(<LoadingState message="Fetching data..." />);
    expect(screen.getByText('Fetching data...')).toBeInTheDocument();
  });

  it('renders ConfirmDialog and responds to confirm/cancel', () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <ConfirmDialog
        open={true}
        title="Delete Item"
        message="Are you sure?"
        confirmText="Delete"
        onConfirm={onConfirm}
        onCancel={onCancel}
        isDestructive={true}
      />
    );
    expect(screen.getByText('Delete Item')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('renders PageHeader with title, subtitle, and action', () => {
    render(
      <PageHeader
        title="Manage Users"
        subtitle="Overview of system accounts"
        action={<button>New User</button>}
      />
    );
    expect(screen.getByText('Manage Users')).toBeInTheDocument();
    expect(screen.getByText('Overview of system accounts')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New User' })).toBeInTheDocument();
  });
});

describe('TST-040 Route Table Structure & Precedence', () => {
  it('defines static routes before parameterized routes', () => {
    const paths = routeTable.map((r) => r.path);
    const unassignedIdx = paths.indexOf('/admin/parcels/unassigned');
    const parcelIdIdx = paths.indexOf('/admin/parcels/:id');
    expect(unassignedIdx).toBeLessThan(parcelIdIdx);

    const pendingMerchantsIdx = paths.indexOf('/admin/merchants/pending');
    const merchantIdIdx = paths.indexOf('/admin/merchants/:id');
    expect(pendingMerchantsIdx).toBeLessThan(merchantIdIdx);
  });

  it('maps correct role homes', () => {
    expect(ROLE_HOMES.CUSTOMER).toBe('/customer/dashboard');
    expect(ROLE_HOMES.MERCHANT).toBe('/merchant/dashboard');
    expect(ROLE_HOMES.COURIER).toBe('/courier/deliveries');
    expect(ROLE_HOMES.ADMIN).toBe('/admin/dashboard');
  });
});

describe('TST-040 Auth Forms Rendering & Validation', () => {
  it('renders LoginPage with email, password fields and login button', () => {
    renderWithProviders(<LoginPage />);
    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Email Address/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText('••••••••••••')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Login' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Continue with Google/i })).toBeInTheDocument();
  });

  it('renders RegisterPage with full name, email, phone, password and policy hints', () => {
    renderWithProviders(<RegisterPage />);
    expect(screen.getByRole('heading', { name: 'Create Account' })).toBeInTheDocument();
    expect(screen.getByLabelText(/Full Name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Email Address/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Phone Number/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/At least 10 characters/i)).toBeInTheDocument();
    expect(screen.getByText(/10-64 chars/)).toBeInTheDocument();
    expect(screen.getByText(/Uppercase/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create Account' })).toBeInTheDocument();
  });
});
