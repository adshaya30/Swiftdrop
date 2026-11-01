import { useState } from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import PublicLayout from './layouts/PublicLayout';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import './App.css';

function HomePage() {
  const { user, isAuthenticated, isInitializing, logout } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      setLoggingOut(false);
    }
  };

  if (isInitializing) {
    return (
      <main className="home-page">
        <section className="welcome-card" aria-labelledby="welcome-title">
          <p className="brand-mark" aria-hidden="true">SD</p>
          <h1 id="welcome-title">SwiftDrop</h1>
          <p className="welcome-subtitle">Restoring session…</p>
        </section>
      </main>
    );
  }

  return (
    <main className="home-page">
      <section className="welcome-card" aria-labelledby="welcome-title">
        <p className="brand-mark" aria-hidden="true">SD</p>
        <h1 id="welcome-title">
          {isAuthenticated ? `Welcome, ${user?.fullName}!` : 'Hello, SwiftDrop!'}
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
              Role: <strong>{user?.role}</strong> &bull; User ID: <code style={{ fontSize: '0.8rem' }}>{user?.id}</code>
            </div>

            <button
              onClick={handleLogout}
              disabled={loggingOut}
              style={{
                display: 'inline-block',
                padding: '0.75rem 2rem',
                background: '#dc2626',
                color: '#ffffff',
                border: 'none',
                borderRadius: '0.5rem',
                fontWeight: 600,
                cursor: loggingOut ? 'not-allowed' : 'pointer',
                opacity: loggingOut ? 0.7 : 1,
                transition: 'background 0.2s ease',
              }}
            >
              {loggingOut ? 'Signing Out…' : 'Sign Out'}
            </button>
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

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Home */}
          <Route path="/" element={<HomePage />} />

          {/* Customer Registration – FR-AUTH-001 / FR-AUTH-002 */}
          <Route
            path="/register"
            element={
              <PublicLayout>
                <RegisterPage />
              </PublicLayout>
            }
          />

          {/* Customer Login – FR-AUTH-003, FR-AUTH-004, FR-AUTH-005 */}
          <Route
            path="/login"
            element={
              <PublicLayout>
                <LoginPage />
              </PublicLayout>
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
