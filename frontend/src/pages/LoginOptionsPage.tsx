import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getAuthConfig } from '../services/authService';
import PublicLayout from '../layouts/PublicLayout';
import SwiftDropLogo from '../components/SwiftDropLogo';
import heroCourierImg from '../assets/hero-courier.jpg';

type GoogleAuthState = 'loading' | 'enabled' | 'disabled' | 'error';

/**
 * Login Options Gateway Page — /login
 * 2-column hero card matching the homepage design system.
 * Left: Auth selection (Google OAuth + Email).
 * Right: Pristine framed courier delivery visual.
 */
export default function LoginOptionsPage() {
  const [googleStatus, setGoogleStatus] = useState<GoogleAuthState>('loading');

  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').trim();
  const googleLoginUrl = apiBase && !apiBase.startsWith('/')
    ? `${apiBase.replace(/\/api\/v1\/?$/, '')}/api/v1/auth/google/login`
    : '/api/v1/auth/google/login';

  useEffect(() => {
    let active = true;
    getAuthConfig()
      .then(config => {
        if (active) setGoogleStatus(config.googleEnabled ? 'enabled' : 'disabled');
      })
      .catch(() => {
        if (active) setGoogleStatus('error');
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <PublicLayout>
      <div className="home-hero-container">
        <section className="home-hero-card" aria-labelledby="login-hero-title">
          {/* Left Content Area */}
          <div className="hero-content auth-hero-content">
            <div className="auth-brand-badge">
              <SwiftDropLogo variant="dark" size="lg" />
              <p className="auth-brand-tagline">Deliver smarter. Live better.</p>
            </div>

            <h1 id="login-hero-title" className="hero-title" style={{ marginBottom: '0.5rem' }}>
              <span className="hero-title-navy">Fast Delivery.</span>
              <span className="hero-title-orange">Everywhere.</span>
            </h1>

            <p className="hero-subtitle" style={{ marginBottom: '1.75rem' }}>
              Sign in to manage deliveries, track parcels, and access your dashboard.
            </p>

            {/* Auth Action Stack */}
            <div className="auth-action-stack">
              <div className="auth-google-slot">
                {googleStatus === 'enabled' ? (
                  <a href={googleLoginUrl} className="auth-btn-google">
                    <span className="auth-google-icon-circle" aria-hidden="true">
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
                ) : (
                  <>
                    <button
                      type="button"
                      className="auth-btn-google auth-btn-google--disabled"
                      disabled
                      aria-disabled="true"
                    >
                      <span className="auth-google-icon-circle" aria-hidden="true">
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
                    <p className="auth-unavailable-hint" role="status">
                      Google sign-in is temporarily unavailable.
                    </p>
                  </>
                )}
              </div>

              <div className="auth-divider">
                <span>or</span>
              </div>

              {/* Email Login Button */}
              <Link to="/login/email" className="auth-btn-primary-action">
                Login with Email
              </Link>

              <div className="auth-card-footer">
                <p>
                  Don&apos;t have an account?{' '}
                  <Link to="/register" className="auth-signup-link">
                    Sign Up
                  </Link>
                </p>
              </div>
            </div>
          </div>

          {/* Right Visual Area: EXACTLY like the second screenshot */}
          <div className="hero-visual">
            <div className="hero-visual-frame">
              <img
                src={heroCourierImg}
                alt="SwiftDrop courier delivering parcel"
                className="hero-courier-img"
                loading="eager"
              />
            </div>
          </div>
        </section>
      </div>
    </PublicLayout>
  );
}
