import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '../context/AuthContext';
import * as profileApi from '../api/profile';
import * as adminUsersApi from '../api/adminUsers';
import ProfilePage from '../pages/auth/profile/index.jsx';
import ChangePasswordPage from '../pages/auth/changePassword/index.jsx';
import AdminUsersPage from '../pages/admin/users/index.jsx';
import AuthCallbackPage from '../pages/auth/callback/index.jsx';

// Mock API modules
vi.mock('../api/profile');
vi.mock('../api/adminUsers');
vi.mock('../api/auth', () => ({
  refresh: vi.fn().mockResolvedValue({
    accessToken: 'mock-token',
    user: { id: 'user-1', fullName: 'Kasun Perera', role: 'CUSTOMER' },
  }),
  login: vi.fn(),
  logout: vi.fn().mockResolvedValue(),
  getAuthConfig: vi.fn().mockResolvedValue({ googleEnabled: true }),
}));

function renderWithProviders(ui) {
  return render(
    <BrowserRouter>
      <AuthProvider>{ui}</AuthProvider>
    </BrowserRouter>
  );
}

describe('TST-040 Member #2 Frontend Pages & Flows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('TST-040 OAuth Callback Page', () => {
    it('restores session and renders loading state', async () => {
      renderWithProviders(<AuthCallbackPage />);
      expect(screen.getByText(/Completing sign in/i)).toBeInTheDocument();
    });
  });

  describe('TST-040 Profile Page', () => {
    it('renders profile data and handles edit mode', async () => {
      profileApi.getMe.mockResolvedValueOnce({
        id: 'u-1',
        fullName: 'Kasun Perera',
        email: 'kasun@example.lk',
        phone: '+94771234567',
        role: 'CUSTOMER',
        authProvider: 'LOCAL',
        createdAt: '2026-11-01T10:00:00Z',
      });

      renderWithProviders(<ProfilePage />);

      await waitFor(() => {
        expect(screen.getByText('Kasun Perera')).toBeInTheDocument();
        expect(screen.getByText('kasun@example.lk')).toBeInTheDocument();
      });

      const editBtn = screen.getByRole('button', { name: /Edit Profile/i });
      fireEvent.click(editBtn);

      expect(screen.getByDisplayValue('Kasun Perera')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Save Changes/i })).toBeInTheDocument();
    });
  });

  describe('TST-040 Change Password Page', () => {
    it('validates live checklist requirements', async () => {
      profileApi.getMe.mockResolvedValueOnce({
        email: 'kasun@example.lk',
      });

      renderWithProviders(<ChangePasswordPage />);

      expect(screen.getByPlaceholderText('Enter current password')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('At least 10 characters with upper, lower, digit & special')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('Re-enter new password')).toBeInTheDocument();

      // Requirements checklist rendered
      expect(screen.getByText(/10 to 64 characters/i)).toBeInTheDocument();
      expect(screen.getByText(/At least 1 uppercase/i)).toBeInTheDocument();
      expect(screen.getByText(/At least 1 lowercase/i)).toBeInTheDocument();
      expect(screen.getByText(/At least 1 number/i)).toBeInTheDocument();
      expect(screen.getByText(/At least 1 special character/i)).toBeInTheDocument();
      expect(screen.getByText(/Different from current/i)).toBeInTheDocument();
      expect(screen.getByText(/No email address in password/i)).toBeInTheDocument();
      expect(screen.getByText(/Passwords match/i)).toBeInTheDocument();
    });
  });

  describe('TST-040 Admin Users Page', () => {
    it('renders user table with filters and loads user list', async () => {
      adminUsersApi.getUsers.mockResolvedValueOnce({
        content: [
          {
            id: 'u-10',
            fullName: 'Kamal Perera',
            email: 'kamal@example.lk',
            role: 'COURIER',
            status: 'ACTIVE',
            phone: '+94771234567',
            courierProfile: {
              vehicleType: 'MOTORBIKE',
              vehicleNumber: 'WP-BBI-1234',
            },
            createdAt: '2026-11-01T12:00:00Z',
          },
        ],
        totalElements: 1,
      });

      renderWithProviders(<AdminUsersPage />);

      await waitFor(() => {
        expect(screen.getByText('Kamal Perera')).toBeInTheDocument();
        expect(screen.getByText('kamal@example.lk')).toBeInTheDocument();
        expect(screen.getByText(/WP-BBI-1234/i)).toBeInTheDocument();
      });

      expect(screen.getByRole('button', { name: /Add Courier/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Add Admin/i })).toBeInTheDocument();
    });

    it('opens Add Courier dialog with vehicle fields', async () => {
      adminUsersApi.getUsers.mockResolvedValueOnce({
        content: [],
        totalElements: 0,
      });

      renderWithProviders(<AdminUsersPage />);

      const addCourierBtn = await screen.findByRole('button', { name: /Add Courier/i });
      fireEvent.click(addCourierBtn);

      expect(screen.getByText(/Create New Courier/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Vehicle Number/i)).toBeInTheDocument();
    });
  });
});
