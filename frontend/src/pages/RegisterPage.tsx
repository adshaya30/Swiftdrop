import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { registerCustomer, getAuthConfig } from '../services/authService';
import type { ApiError } from '../services/authService';
import axios from 'axios';
import SwiftDropLogo from '../components/SwiftDropLogo';
import './RegisterPage.css';

type GoogleAuthState = 'loading' | 'enabled' | 'disabled' | 'error';

interface FormData {
  fullName: string;
  email: string;
  phone: string;
  password: string;
}

interface FormErrors {
  fullName?: string;
  email?: string;
  phone?: string;
  password?: string;
  general?: string;
}

/**
 * Customer Registration page — /register
 * SRS v2.1 – FR-AUTH-001, FR-AUTH-002
 *
 * Connects to POST /api/v1/auth/register via the shared Axios client.
 * Does NOT issue JWT or authenticate the user after registration.
 */
export default function RegisterPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormData>({
    fullName: '',
    email: '',
    phone: '',
    password: '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [successEmail, setSuccessEmail] = useState('');
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

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  function setField(field: keyof FormData, value: string) {
    setForm(prev => ({ ...prev, [field]: value }));
    // Clear the error for that field as user types
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }
  }

  function clientValidate(): boolean {
    const e: FormErrors = {};
    const f = form;

    if (!f.fullName.trim()) {
      e.fullName = 'Full name is required.';
    } else if (f.fullName.trim().length < 2) {
      e.fullName = 'Full name must be at least 2 characters.';
    } else if (f.fullName.trim().length > 100) {
      e.fullName = 'Full name must not exceed 100 characters.';
    }

    if (!f.email.trim()) {
      e.email = 'Email address is required.';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) {
      e.email = 'Please enter a valid email address.';
    }

    if (!f.phone.trim()) {
      e.phone = 'Phone number is required.';
    } else if (!/^(\+94|0094|0)[1-9][0-9]{7,8}$/.test(f.phone.replace(/[\s\-()\s]/g, ''))) {
      e.phone = 'Enter a valid Sri Lankan phone (e.g. 0771234567 or +94771234567).';
    }

    if (!f.password) {
      e.password = 'Password is required.';
    } else if (f.password.length < 10) {
      e.password = 'Password must be at least 10 characters.';
    } else if (f.password.length > 64) {
      e.password = 'Password must not exceed 64 characters.';
    } else if (!/(?=.*[A-Z])/.test(f.password)) {
      e.password = 'Password must include at least one uppercase letter.';
    } else if (!/(?=.*[a-z])/.test(f.password)) {
      e.password = 'Password must include at least one lowercase letter.';
    } else if (!/(?=.*\d)/.test(f.password)) {
      e.password = 'Password must include at least one digit.';
    } else if (!/(?=.*[^A-Za-z\d])/.test(f.password)) {
      e.password = 'Password must include at least one special character.';
    }

    setErrors(e);
    return Object.keys(e).length === 0;
  }

  // -----------------------------------------------------------------------
  // Submit
  // -----------------------------------------------------------------------

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!clientValidate()) return;

    setLoading(true);
    setErrors({});

    try {
      await registerCustomer({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
      });

      setSuccessEmail(form.email.trim().toLowerCase());
      setSuccess(true);
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        const apiError = err.response?.data as ApiError | undefined;

        if (status === 409) {
          setErrors({
            email: 'An account with this email address already exists. Please sign in.',
          });
        } else if (status === 400 && apiError?.errors?.length) {
          const fieldErrors: FormErrors = {};
          apiError.errors.forEach(fe => {
            if (fe.field === 'fullName') fieldErrors.fullName = fe.message;
            else if (fe.field === 'email') fieldErrors.email = fe.message;
            else if (fe.field === 'phone') fieldErrors.phone = fe.message;
            else if (fe.field === 'password') fieldErrors.password = fe.message;
          });
          setErrors({
            ...fieldErrors,
            general: apiError.detail || 'Validation error. Please check the fields.',
          });
        } else {
          setErrors({
            general:
              apiError?.detail ||
              'A connection or server error occurred. Please try again later.',
          });
        }
      } else {
        setErrors({ general: 'An unexpected error occurred. Please try again.' });
      }
    } finally {
      setLoading(false);
    }
  }

  // -----------------------------------------------------------------------
  // Render – success
  // -----------------------------------------------------------------------

  if (success) {
    return (
      <div className="reg-card" id="reg-success-card">
        <div className="reg-brand-header">
          <SwiftDropLogo variant="dark" size="lg" />
        </div>
        <div className="reg-success-icon" aria-hidden="true">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#16A34A" strokeWidth="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
            <polyline points="22 4 12 14.01 9 11.01" />
          </svg>
        </div>
        <h2 className="reg-title" id="reg-success-heading">Registration Successful</h2>
        <p className="reg-success-body">
          Your customer account for <strong>{successEmail}</strong> has been created.
        </p>
        <p className="reg-success-hint">
          Please check your email to verify your account or proceed to sign in.
        </p>
        <button
          id="reg-go-login-btn"
          className="reg-btn reg-btn--primary"
          onClick={() => navigate('/login')}
        >
          Go to Login
        </button>
      </div>
    );
  }

  // -----------------------------------------------------------------------
  // Render – form
  // -----------------------------------------------------------------------

  return (
    <div className="reg-card">
      <div className="reg-brand-header">
        <SwiftDropLogo variant="dark" size="lg" />
        <p className="reg-tagline">Deliver smarter. Live better.</p>
      </div>

      <h1 className="reg-title" id="reg-heading">Create Account</h1>
      <p className="reg-subtitle">Join SwiftDrop and start shipping parcels across Sri Lanka.</p>

      {/* Google OAuth Option */}
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
        <span>or register with email</span>
      </div>

      {errors.general && (
        <div className="reg-alert" role="alert" aria-live="assertive">
          {errors.general}
        </div>
      )}

      <form
        id="reg-form"
        className="reg-form"
        onSubmit={handleSubmit}
        noValidate
        aria-labelledby="reg-heading"
      >
        {/* Full Name */}
        <div className="reg-field">
          <label htmlFor="reg-fullName" className="reg-label">
            Full Name
          </label>
          <input
            id="reg-fullName"
            name="fullName"
            type="text"
            autoComplete="name"
            required
            aria-required="true"
            aria-describedby={errors.fullName ? 'reg-fullName-err' : undefined}
            aria-invalid={!!errors.fullName}
            className={`reg-input ${errors.fullName ? 'reg-input--error' : ''}`}
            placeholder="e.g. Kasun Perera"
            value={form.fullName}
            onChange={e => setField('fullName', e.target.value)}
            disabled={loading}
            maxLength={100}
          />
          {errors.fullName && (
            <span id="reg-fullName-err" className="reg-error" role="alert">
              {errors.fullName}
            </span>
          )}
        </div>

        {/* Email */}
        <div className="reg-field">
          <label htmlFor="reg-email" className="reg-label">
            Email Address
          </label>
          <input
            id="reg-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            aria-required="true"
            aria-describedby={errors.email ? 'reg-email-err' : undefined}
            aria-invalid={!!errors.email}
            className={`reg-input ${errors.email ? 'reg-input--error' : ''}`}
            placeholder="e.g. kasun.perera@example.lk"
            value={form.email}
            onChange={e => setField('email', e.target.value)}
            disabled={loading}
            maxLength={254}
          />
          {errors.email && (
            <span id="reg-email-err" className="reg-error" role="alert">
              {errors.email}
            </span>
          )}
        </div>

        {/* Phone */}
        <div className="reg-field">
          <label htmlFor="reg-phone" className="reg-label">
            Phone Number
          </label>
          <input
            id="reg-phone"
            name="phone"
            type="tel"
            autoComplete="tel"
            required
            aria-required="true"
            aria-describedby={errors.phone ? 'reg-phone-err' : 'reg-phone-hint'}
            aria-invalid={!!errors.phone}
            className={`reg-input ${errors.phone ? 'reg-input--error' : ''}`}
            placeholder="0771234567 or +94771234567"
            value={form.phone}
            onChange={e => setField('phone', e.target.value)}
            disabled={loading}
          />
          {errors.phone ? (
            <span id="reg-phone-err" className="reg-error" role="alert">
              {errors.phone}
            </span>
          ) : (
            <span id="reg-phone-hint" className="reg-hint">
              Sri Lankan numbers: 07XXXXXXXX or +94XXXXXXXXX
            </span>
          )}
        </div>

        {/* Password */}
        <div className="reg-field">
          <label htmlFor="reg-password" className="reg-label">
            Password
          </label>
          <div className="reg-password-wrap">
            <input
              id="reg-password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              required
              aria-required="true"
              aria-describedby={errors.password ? 'reg-password-err' : 'reg-password-hint'}
              aria-invalid={!!errors.password}
              className={`reg-input reg-input--pw ${errors.password ? 'reg-input--error' : ''}`}
              placeholder="Min. 10 characters"
              value={form.password}
              onChange={e => setField('password', e.target.value)}
              disabled={loading}
              maxLength={64}
            />
            <button
              id="reg-toggle-password"
              type="button"
              className="reg-pw-toggle"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              onClick={() => setShowPassword(s => !s)}
              tabIndex={0}
            >
              {showPassword ? (
                <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
                  <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              ) : (
                <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
              )}
            </button>
          </div>
          {errors.password ? (
            <span id="reg-password-err" className="reg-error" role="alert">
              {errors.password}
            </span>
          ) : (
            <span id="reg-password-hint" className="reg-hint">
              10–64 chars, uppercase, lowercase, digit, and special character.
            </span>
          )}
        </div>

        {/* Submit */}
        <button
          id="reg-submit-btn"
          type="submit"
          className="reg-btn reg-btn--primary reg-btn--full"
          disabled={loading}
          aria-busy={loading}
        >
          {loading ? (
            <>
              <span className="reg-spinner" aria-hidden="true" />
              Creating account…
            </>
          ) : (
            'Create Account'
          )}
        </button>
      </form>

      <p className="reg-login-link">
        Already have an account?{' '}
        <Link to="/login" id="reg-login-link">Sign In</Link>
      </p>
    </div>
  );
}
