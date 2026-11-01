import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PublicLayout from './layouts/PublicLayout';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import './App.css';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* Home – existing welcome screen */}
          <Route
            path="/"
            element={
              <main className="home-page">
                <section className="welcome-card" aria-labelledby="welcome-title">
                  <p className="brand-mark" aria-hidden="true">SD</p>
                  <h1 id="welcome-title">Hello, SwiftDrop!</h1>
                  <p className="welcome-subtitle">
                    Parcel Delivery &amp; Courier Management System
                  </p>
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
                </section>
              </main>
            }
          />

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
