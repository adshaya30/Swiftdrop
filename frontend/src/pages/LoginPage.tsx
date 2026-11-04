import { useState, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import type { ApiError } from '../services/authService';
import SwiftDropLogo from '../components/SwiftDropLogo';
import './LoginPage.css';

interface LoginForm {
  email: string;
  password: string;
}

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
 * Customer Login Page — /login/email
 * SRS v2.1 – FR-AUTH-003, FR-AUTH-004, FR-AUTH-005
 *
 * Authenticates user via POST /api/v1/auth/login.
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
