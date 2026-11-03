import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import PublicLayout from './layouts/PublicLayout';
import AppLayout from './layouts/AppLayout';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import ProfilePage from './pages/ProfilePage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import ProtectedRoute from './components/ProtectedRoute';
import PublicOnlyRoute from './components/PublicOnlyRoute';
import LoadingSpinner from './components/LoadingSpinner';
import EmptyState from './components/EmptyState';
import { getRoleHomePath } from './utils/roleUtils';
import './App.css';

/**
 * Public Landing / Welcome View
 */
function HomePage() {
  const { user, isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return <LoadingSpinner fullScreen label="Restoring session…" />;
  }

  const roleHome = getRoleHomePath(user?.role);

  return (
    <main className="home-page">
      <section className="welcome-card" aria-labelledby="welcome-title">
        <p className="brand-mark" aria-hidden="true">SD</p>
        <h1 id="welcome-title">
          {isAuthenticated ? `Welcome back, ${user?.fullName}!` : 'Hello, SwiftDrop!'}
        </h1>
        <p className="welcome-subtitle">
          Parcel Delivery &amp; Courier Management System
        </p>

        {isAuthenticated ? (
          <div style={{ marginTop: '2rem', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1rem' }}>
            <div
              style={{
                padding: '0.5rem 1rem',
                background: 'rgba(37, 99, 235, 0.15)',
                border: '1px solid rgba(37, 99, 235, 0.4)',
                borderRadius: '0.375rem',
                color: '#93c5fd',
                fontSize: '0.875rem',
              }}
            >
              Active Role: <strong>{user?.role}</strong> &bull; User ID: <code style={{ fontSize: '0.8rem' }}>{user?.id}</code>
            </div>

            <Link
              to={roleHome}
              style={{
                display: 'inline-block',
                padding: '0.75rem 2rem',
                background: '#2563eb',
                color: '#ffffff',
                borderRadius: '0.5rem',
                textDecoration: 'none',
                fontWeight: 600,
                transition: 'background 0.2s ease',
              }}
            >
              Go to {user?.role} Portal &rarr;
            </Link>
          </div>
        ) : (
          <div style={{ marginTop: '2rem', display: 'flex', gap: '1rem', justifyContent: 'center' }}>
            <Link
              to="/login"
              style={{
                display: 'inline-block',
                padding: '0.75rem 1.75rem',
                background: '#2563eb',
                color: '#ffffff',
                borderRadius: '0.5rem',
                textDecoration: 'none',
                fontWeight: 600,
                transition: 'background 0.2s ease',
              }}
            >
              Sign In &rarr;
            </Link>
            <Link
              to="/register"
              style={{
                display: 'inline-block',
                padding: '0.75rem 1.75rem',
                background: 'rgba(255, 255, 255, 0.08)',
                color: '#ffffff',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                borderRadius: '0.5rem',
                textDecoration: 'none',
                fontWeight: 600,
                transition: 'background 0.2s ease',
              }}
            >
              Register as Customer
            </Link>
          </div>
        )}
      </section>
    </main>
  );
}

/**
 * Placeholder component for role-specific portals.
 * Enforces Member 1 route guard boundaries while awaiting page implementations from respective service owners.
 */
function RolePortalPlaceholder({ title, description, icon }: { title: string; description: string; icon: string }) {
  const { user } = useAuth();

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.5rem' }}>{title}</h2>
      <p style={{ color: '#94a3b8', marginBottom: '1.5rem' }}>
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
                    description="Access parcel tracking, order placement, and delivery history. Protected by Member 1 route guards."
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
