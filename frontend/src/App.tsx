import { BrowserRouter, Routes, Route, Navigate, Link } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import PublicLayout from './layouts/PublicLayout';
import RegisterPage from './pages/RegisterPage';
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
                  <div style={{ marginTop: '2rem' }}>
                    <Link
                      to="/register"
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
                      Register as Customer &rarr;
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

          {/* Login stub – navigates here after successful registration (Feature 2) */}
          <Route
            path="/login"
            element={
              <PublicLayout>
                <div style={{ color: '#94a3b8', textAlign: 'center', padding: '2rem' }}>
                  <h1 style={{ color: '#f8fafc', marginBottom: '0.5rem' }}>Sign In</h1>
                  <p>Login is coming in Feature 2.</p>
                </div>
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



