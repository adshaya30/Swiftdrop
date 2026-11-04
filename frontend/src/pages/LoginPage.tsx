import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { getAuthConfig, type ApiError } from '../services/authService';
import SwiftDropLogo from '../components/SwiftDropLogo';
import './LoginPage.css';

interface LoginForm {
  email: string;
  password: string;
}

type GoogleAuthState = 'loading' | 'enabled' | 'disabled' | 'error';

interface FormErrors {
  email?: string;
  password?: string;
  general?: string;
}

function getOAuthErrorMessage(errorParam: string | null): string | undefined {
  if (errorParam === 'google_denied') {
    return 'Google sign-in was cancelled or access was denied.';
  }
  if (errorParam === 'account_exists') {
    return 'An account already exists with this email address. Please sign in with your email and password.';
  }
  if (errorParam === 'account_deactivated') {
    return 'Your account has been deactivated. Please contact customer support.';
  }
  if (errorParam === 'google_failed') {
    return 'Google authentication failed. Please try again or sign in with your email.';
  }

  return undefined;
}

/**
 * Customer Login Page — /login
 * SRS v2.1 – FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-009, FR-AUTH-010, FR-AUTH-011
 *
 * Authenticates user via POST /api/v1/auth/login or Google OAuth 2.0.
 * Stores RS256 JWT access token in memory (never localStorage/sessionStorage).
 * Receives HttpOnly 'sd_refresh' cookie automatically.
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [form, setForm] = useState<LoginForm>({
    email: '',
    password: '',
  });
  const [errors, setErrors] = useState<FormErrors>(() => {
    const oauthError = getOAuthErrorMessage(new URLSearchParams(window.location.search).get('error'));
    return oauthError ? { general: oauthError } : {};
  });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [retryAfterSeconds, setRetryAfterSeconds] = useState<number | null>(null);
  const [googleStatus, setGoogleStatus] = useState<GoogleAuthState>('loading');

  const apiBase = (import.meta.env.VITE_API_BASE_URL || '').trim();
  const googleLoginUrl = apiBase && !apiBase.startsWith('/')
    ? `${apiBase.replace(/\/api\/v1\/?$/, '')}/api/v1/auth/google/login`
    : '/api/v1/auth/google/login';

  // Check public auth configuration for Google OAuth availability (FR-AUTH-011)
  useEffect(() => {
    let active = true;
    getAuthConfig()
      .then(config => {
        if (active) {
          setGoogleStatus(config.googleEnabled ? 'enabled' : 'disabled');
        }
      })
      .catch(() => {
        // Fallback to error/disabled if endpoint unreachable
        if (active) {
          setGoogleStatus('error');
        }
      });
    return () => {
      active = false;
    };
  }, []);

  function setField(field: keyof LoginForm, value: string) {
    setForm(prev => ({ ...prev, [field]: value }));
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }
    if (errors.general) {
      setErrors(prev => ({ ...prev, general: undefined }));
    }
  }

  function clientValidate(): boolean {
    const errs: FormErrors = {};

    if (!form.email.trim()) {
      errs.email = 'Email address is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
      errs.email = 'Please enter a valid email address.';
    }

    if (!form.password) {
      errs.password = 'Password is required.';
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!clientValidate()) return;

    setLoading(true);
    setErrors({});
    setRetryAfterSeconds(null);

    try {
      await login({
        email: form.email.trim().toLowerCase(),
        password: form.password,
      });

      // Navigate to homepage / dashboard on successful login
      navigate('/', { replace: true });
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const apiError = err.response?.data as ApiError | undefined;

        if (status === 401) {
          setErrors({
            general: apiError?.detail || 'Invalid email or password. Please check your credentials.',
          });
        } else if (status === 429) {
          const retryHeader = err.response?.headers?.['retry-after'];
          const seconds = retryHeader ? parseInt(retryHeader, 10) : 900;
          setRetryAfterSeconds(seconds);
          const minutes = Math.ceil(seconds / 60);
          setErrors({
            general:
              apiError?.detail ||
              `Account is temporarily locked due to consecutive failed login attempts. Please try again in ${minutes} minute${minutes > 1 ? 's' : ''}.`,
          });
        } else if (status === 403) {
          setErrors({
            general:
              apiError?.detail ||
              'Access denied. Your account is deactivated or pending merchant approval.',
          });
        } else if (status === 400 && apiError?.errors?.length) {
          const fieldErrs: FormErrors = {};
          apiError.errors.forEach(fe => {
            if (fe.field === 'email') fieldErrs.email = fe.message;
            else if (fe.field === 'password') fieldErrs.password = fe.message;
          });
          setErrors({
            ...fieldErrs,
            general: apiError.detail || 'Validation error. Please review the highlighted fields.',
          });
        } else {
          setErrors({
            general:
              apiError?.detail ||
              'A connection or server error occurred. Please check your internet connection and try again.',
          });
        }
      } else {
        setErrors({ general: 'An unexpected error occurred. Please try again.' });
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-card">
      <Link to="/login" className="auth-back-link">
        &larr; All sign-in options
      </Link>

      <div className="login-brand-header">
        <SwiftDropLogo variant="dark" size="lg" />
        <p className="login-tagline">Deliver smarter. Live better.</p>
      </div>

      <h1 className="login-title">Login</h1>
      <p className="login-subtitle">Enter your credentials to access your SwiftDrop account</p>

      {/* General Alert */}
      {errors.general && (
        <div
          role="alert"
          aria-live="assertive"
          className={`login-alert ${retryAfterSeconds ? 'login-alert-lockout' : 'login-alert-error'}`}
        >
          {retryAfterSeconds && <span className="alert-icon" aria-hidden="true">🔒</span>}
          <span>{errors.general}</span>
        </div>
      )}

      {/* Google OAuth Login Button (FR-AUTH-009, FR-AUTH-011) */}
      <div className="google-login-container">
        {googleStatus === 'enabled' ? (
          <a href={googleLoginUrl} className="google-login-btn">
            <svg className="google-icon" viewBox="0 0 24 24" aria-hidden="true">
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
            <span>Continue with Google</span>
          </a>
        ) : googleStatus === 'loading' ? (
          <button
            type="button"
            className="google-login-btn google-login-btn--disabled"
            disabled
            aria-disabled="true"
          >
            <svg className="google-icon" viewBox="0 0 24 24" aria-hidden="true">
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
            <span>Continue with Google</span>
            <span className="google-status-spinner" aria-hidden="true" />
          </button>
        ) : (
          <>
            <button
              type="button"
              className="google-login-btn google-login-btn--disabled"
              disabled
              aria-disabled="true"
            >
              <svg className="google-icon" viewBox="0 0 24 24" aria-hidden="true">
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
              <span>Continue with Google</span>
            </button>
            <p className="google-unavailable-hint" role="status">
              Google sign-in is temporarily unavailable.
            </p>
          </>
        )}
      </div>

      <div className="login-divider">
        <span>or continue with email</span>
      </div>

      <form className="login-form" onSubmit={handleSubmit} noValidate>
        {/* Email */}
        <div className="login-field">
          <label className="login-label" htmlFor="login-email">
            Email Address
          </label>
          <input
            id="login-email"
            type="email"
            autoComplete="email"
            className={`login-input ${errors.email ? 'input-error' : ''}`}
            placeholder="e.g. kasun.perera@example.lk"
            value={form.email}
            onChange={e => setField('email', e.target.value)}
            disabled={loading}
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? 'login-email-err' : undefined}
          />
          {errors.email && (
            <span id="login-email-err" role="alert" className="field-error">
              {errors.email}
            </span>
          )}
        </div>

        {/* Password */}
        <div className="login-field">
          <div className="label-row">
            <label className="login-label" htmlFor="login-password">
              Password
            </label>
          </div>
          <div className="password-wrapper">
            <input
              id="login-password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              className={`login-input ${errors.password ? 'input-error' : ''}`}
              placeholder="••••••••••••"
              value={form.password}
              onChange={e => setField('password', e.target.value)}
              disabled={loading}
              aria-invalid={!!errors.password}
              aria-describedby={errors.password ? 'login-pwd-err' : undefined}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword(p => !p)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              tabIndex={0}
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
          {errors.password && (
            <span id="login-pwd-err" role="alert" className="field-error">
              {errors.password}
            </span>
          )}
        </div>

        {/* Submit */}
        <button
          type="submit"
          className="login-submit-btn"
          disabled={loading || retryAfterSeconds !== null}
        >
          {loading ? (
            <span className="btn-spinner" aria-label="Signing in…">
              Signing in…
            </span>
          ) : (
            'Login'
          )}
        </button>
      </form>

      {/* Switch to Register */}
      <div className="login-footer">
        <p>
          Don&apos;t have an account?{' '}
          <Link to="/register" className="login-link">
            Sign Up
          </Link>
        </p>
      </div>
    </div>
  );
}
