import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
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

/**
 * Public Landing / Welcome View — Shared frontend foundation
 * Redesigned with SwiftDrop brand identity, official transparent logo, and tokens.
 */
function HomePage() {
  const { user, isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return <LoadingSpinner fullScreen label="Restoring session…" />;
  }

  const roleHome = getRoleHomePath(user?.role);
  const roleLabel = user?.role ? (ROLE_LABELS[user.role as UserRole] || user.role) : '';

  return (
    <PublicLayout>
      <div className="home-page">
        <section className="welcome-card" aria-labelledby="welcome-title">
          <div className="welcome-logo-wrap">
            <SwiftDropLogo variant="dark" size="lg" />
            <p className="welcome-tagline">Deliver smarter. Live better.</p>
          </div>

          <h1 id="welcome-title">
            {isAuthenticated ? `Welcome back, ${user?.fullName}!` : 'Fast Delivery. Everywhere.'}
          </h1>
          <p className="welcome-subtitle">
            Island-wide parcel delivery &amp; courier management platform
          </p>

          {isAuthenticated ? (
            <div className="welcome-auth-actions">
              <div className="welcome-role-badge">
                Active Role: <strong>{roleLabel}</strong> &bull; User ID: <code>{user?.id}</code>
              </div>

              <Link to={roleHome} className="sd-btn sd-btn-primary welcome-portal-btn">
                Go to {roleLabel} Portal &rarr;
              </Link>
            </div>
          ) : (
            <div className="welcome-guest-actions">
              <Link to="/login" className="sd-btn sd-btn-primary">
                Sign In &rarr;
              </Link>
              <Link to="/register" className="sd-btn sd-btn-secondary">
                Register as Customer
              </Link>
            </div>
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
