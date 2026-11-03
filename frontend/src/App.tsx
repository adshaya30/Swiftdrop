import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PublicLayout from './layouts/PublicLayout';
import AppLayout from './layouts/AppLayout';
import HomePage from './pages/HomePage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import ProfilePage from './pages/ProfilePage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import AdminUsersPage from './pages/AdminUsersPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import MerchantDashboardPage from './pages/MerchantDashboardPage';
import CourierDashboardPage from './pages/CourierDashboardPage';
import CustomerDashboardPage from './pages/CustomerDashboardPage';
import ProtectedRoute from './components/ProtectedRoute';
import PublicOnlyRoute from './components/PublicOnlyRoute';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public Home Landing Page (Matches Top-Left & Top-Right of Reference Image) */}
          <Route
            path="/"
            element={
              <PublicLayout>
                <HomePage />
              </PublicLayout>
            }
          />

          {/* Public-Only: Registration */}
          <Route
            path="/register"
            element={
              <PublicOnlyRoute>
                <PublicLayout>
                  <RegisterPage />
                </PublicLayout>
              </PublicOnlyRoute>
            }
          />

          {/* Public-Only: Login (Matches Bottom-Center of Reference Image) */}
          <Route
            path="/login"
            element={
              <PublicOnlyRoute>
                <PublicLayout>
                  <LoginPage />
                </PublicLayout>
              </PublicOnlyRoute>
            }
          />

          {/* Google OAuth Callback (Flow 4.5, FR-AUTH-009) */}
          <Route
            path="/auth/callback"
            element={
              <PublicLayout>
                <AuthCallbackPage />
              </PublicLayout>
            }
          />

          {/* Profile Management (FR-AUTH-012) */}
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <AppLayout>
                  <ProfilePage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Change Password / Forced Password Change (FR-AUTH-013, FR-AUTH-014) */}
          <Route
            path="/change-password"
            element={
              <ProtectedRoute>
                <AppLayout>
                  <ChangePasswordPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Customer Portal (Protected: CUSTOMER role) */}
          <Route
            path="/customer/*"
            element={
              <ProtectedRoute allowedRoles={['CUSTOMER']}>
                <AppLayout>
                  <CustomerDashboardPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Merchant Hub (Protected: MERCHANT role - Matches Middle-Left of Reference Image) */}
          <Route
            path="/merchant/*"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT']}>
                <AppLayout>
                  <MerchantDashboardPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Courier Portal (Protected: COURIER role - Matches Bottom-Left Mobile of Reference Image) */}
          <Route
            path="/courier/*"
            element={
              <ProtectedRoute allowedRoles={['COURIER']}>
                <AppLayout>
                  <CourierDashboardPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Admin User Management (Protected: ADMIN role) */}
          <Route
            path="/admin/users"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AppLayout>
                  <AdminUsersPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Operations / Admin Console (Protected: ADMIN role - Matches Middle-Right of Reference Image) */}
          <Route
            path="/admin/*"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AppLayout>
                  <AdminDashboardPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Catch-all */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
