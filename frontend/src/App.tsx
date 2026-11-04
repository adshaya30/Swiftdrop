import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { getAuthConfig } from './services/authService';
import PublicLayout from './layouts/PublicLayout';
import AppLayout from './layouts/AppLayout';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import ProfilePage from './pages/ProfilePage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import AdminUsersPage from './pages/AdminUsersPage';
import ProtectedRoute from './components/ProtectedRoute';
import PublicOnlyRoute from './components/PublicOnlyRoute';
import LoadingSpinner from './components/LoadingSpinner';
import EmptyState from './components/EmptyState';
import SwiftDropLogo from './components/SwiftDropLogo';
import { getRoleHomePath, ROLE_LABELS, type UserRole } from './utils/roleUtils';
import './App.css';

type GoogleAuthState = 'loading' | 'enabled' | 'disabled' | 'error';

/**
 * Public Landing / Hero View — SwiftDrop Island-wide logistics platform
 * Features branded delivery background with floating centered auth entry card
 * strictly matching the visual reference (media_1793750248284_b89f3ef9.jpg).
 */
function HomePage() {
  const { user, isAuthenticated, isInitializing } = useAuth();
  const [googleStatus, setGoogleStatus] = useState<GoogleAuthState>('loading');

  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').trim();
  const googleLoginUrl = apiBase && !apiBase.startsWith('/')
    ? `${apiBase.replace(/\/api\/v1\/?$/, '')}/api/v1/auth/google/login`
    : '/api/v1/auth/google/login';

  useEffect(() => {
    let active = true;
    getAuthConfig()
      .then(config => {
        if (active) {
          setGoogleStatus(config.googleEnabled ? 'enabled' : 'disabled');
        }
      })
      .catch(() => {
        if (active) {
          setGoogleStatus('error');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  if (isInitializing) {
    return <LoadingSpinner fullScreen label="Restoring session…" />;
  }

  const roleHome = getRoleHomePath(user?.role);
  const roleLabel = user?.role ? (ROLE_LABELS[user.role as UserRole] || user.role) : '';

  return (
    <PublicLayout>
      <div className="landing-hero-backdrop">
        {/* Floating Centered White Card */}
        <section className="landing-auth-card" aria-labelledby="landing-brand-title">
          {/* Logo & Tagline */}
          <div className="landing-card-header">
            <SwiftDropLogo variant="dark" size="lg" />
            <p className="landing-card-tagline">Deliver smarter. Live better.</p>
          </div>

          {isAuthenticated ? (
            <div className="landing-auth-session">
              <div className="landing-role-badge">
                Signed in as <strong>{user?.fullName}</strong> ({roleLabel})
              </div>
              <Link to={roleHome} className="landing-btn-login">
                Go to {roleLabel} Portal &rarr;
              </Link>
            </div>
          ) : (
            <>
              {/* Google Login Button */}
              <div className="landing-google-slot">
                {googleStatus === 'enabled' ? (
                  <a href={googleLoginUrl} className="landing-btn-google">
                    <span className="landing-google-icon-circle" aria-hidden="true">
                      <svg className="google-icon" viewBox="0 0 24 24">
                        <path
                          fill="#4285F4"
                          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                        />
                        <path
                          fill="#34A853"
                          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                        />
                        <path
                          fill="#FBBC05"
                          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                        />
                        <path
                          fill="#EA4335"
                          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                        />
                      </svg>
                    </span>
                    <span>Login with Google</span>
                  </a>
                ) : googleStatus === 'loading' ? (
                  <button
                    type="button"
                    className="landing-btn-google landing-btn-google--disabled"
                    disabled
                    aria-disabled="true"
                  >
                    <span className="landing-google-icon-circle" aria-hidden="true">
                      <svg className="google-icon" viewBox="0 0 24 24">
                        <path
                          fill="#4285F4"
                          d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                        />
                        <path
                          fill="#34A853"
                          d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                        />
                        <path
                          fill="#FBBC05"
                          d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                        />
                        <path
                          fill="#EA4335"
                          d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                        />
                      </svg>
                    </span>
                    <span>Login with Google</span>
                    <span className="landing-status-spinner" aria-hidden="true" />
                  </button>
                ) : (
                  <>
                    <button
                      type="button"
                      className="landing-btn-google landing-btn-google--disabled"
                      disabled
                      aria-disabled="true"
                    >
                      <span className="landing-google-icon-circle" aria-hidden="true">
                        <svg className="google-icon" viewBox="0 0 24 24">
                          <path
                            fill="#4285F4"
                            d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                          />
                          <path
                            fill="#34A853"
                            d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                          />
                          <path
                            fill="#FBBC05"
                            d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                          />
                          <path
                            fill="#EA4335"
                            d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                          />
                        </svg>
                      </span>
                      <span>Login with Google</span>
                    </button>
                    <p className="landing-unavailable-hint" role="status">
                      Google sign-in is temporarily unavailable.
                    </p>
                  </>
                )}
              </div>

              {/* Divider */}
              <div className="landing-divider">
                <span>or</span>
              </div>

              {/* Login Button */}
              <Link to="/login" className="landing-btn-login">
                Login
              </Link>

              {/* Sign Up Link */}
              <div className="landing-card-footer">
                <p>
                  Don&apos;t have an account?{' '}
                  <Link to="/register" className="landing-signup-link">
                    Sign Up
                  </Link>
                </p>
              </div>
            </>
          )}
        </section>
      </div>
    </PublicLayout>
  );
}

/**
 * Placeholder component for role-specific portals.
 * Enforces route guard boundaries while awaiting page implementations from respective workstreams.
 */
function RolePortalPlaceholder({ title, description, icon }: { title: string; description: string; icon: string }) {
  const { user } = useAuth();

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <h2 style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--sd-navy, #172033)', marginBottom: '0.5rem' }}>
        {title}
      </h2>
      <p style={{ color: 'var(--sd-text-secondary, #64748B)', marginBottom: '1.5rem' }}>
        Authenticated user: <strong>{user?.fullName}</strong> ({user?.role})
      </p>

      <EmptyState
        icon={icon}
        title={title}
        description={description}
      />
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Public Home */}
          <Route path="/" element={<HomePage />} />

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

          {/* Public-Only: Login */}
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
                  <RolePortalPlaceholder
                    title="Customer Portal"
                    description="Access parcel tracking, order placement, and delivery history. Protected by route guards."
                    icon="📦"
                  />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Merchant Hub (Protected: MERCHANT role) */}
          <Route
            path="/merchant/*"
            element={
              <ProtectedRoute allowedRoles={['MERCHANT']}>
                <AppLayout>
                  <RolePortalPlaceholder
                    title="Merchant Hub"
                    description="Manage merchant bulk deliveries, order dispatches, and warehouse operations."
                    icon="🏪"
                  />
                </AppLayout>
              </ProtectedRoute>
            }
          />

          {/* Courier Portal (Protected: COURIER role) */}
          <Route
            path="/courier/*"
            element={
              <ProtectedRoute allowedRoles={['COURIER']}>
                <AppLayout>
                  <RolePortalPlaceholder
                    title="Courier Delivery Portal"
                    description="View active courier assignments, delivery waypoints, and recipient verification."
                    icon="🚚"
                  />
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

          {/* Admin Console (Protected: ADMIN role) */}
          <Route
            path="/admin/*"
            element={
              <ProtectedRoute allowedRoles={['ADMIN']}>
                <AppLayout>
                  <RolePortalPlaceholder
                    title="Admin Console"
                    description="System-wide administration, user access management, and audit logs."
                    icon="⚙️"
                  />
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
