import { useState, useMemo, useEffect, type FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { changePassword, getProfile, type ApiError } from '../services/authService';
import './ChangePasswordPage.css';

interface PasswordCriteria {
  minLength: boolean;
  hasUpper: boolean;
  hasLower: boolean;
  hasDigit: boolean;
  hasSpecial: boolean;
  differsFromCurrent: boolean;
  noEmailLocalPart: boolean;
  matchesConfirm: boolean;
}

export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [provider, setProvider] = useState<string>(user?.authProvider || 'LOCAL');
  const [userEmail, setUserEmail] = useState<string>(user?.email || '');

  useEffect(() => {
    getProfile()
      .then(profile => {
        setProvider(profile.authProvider);
        setUserEmail(profile.email);
      })
      .catch(() => {
        // Fallback to existing user context
      });
  }, []);

  const isGoogleAccount = provider === 'GOOGLE' || user?.authProvider === 'GOOGLE';
  const isForcedChange = Boolean(user?.mustChangePassword);

  const emailLocalPart = useMemo(() => {
    if (!userEmail) return '';
    return userEmail.split('@')[0].toLowerCase();
  }, [userEmail]);

  const criteria: PasswordCriteria = useMemo(() => {
    return {
      minLength: newPassword.length >= 10 && newPassword.length <= 64,
      hasUpper: /[A-Z]/.test(newPassword),
      hasLower: /[a-z]/.test(newPassword),
      hasDigit: /\d/.test(newPassword),
      hasSpecial: /[^A-Za-z\d]/.test(newPassword),
      differsFromCurrent: newPassword.length > 0 && newPassword !== currentPassword,
      noEmailLocalPart: !emailLocalPart || !newPassword.toLowerCase().includes(emailLocalPart),
      matchesConfirm: newPassword.length > 0 && newPassword === confirmPassword,
    };
  }, [newPassword, currentPassword, confirmPassword, emailLocalPart]);

  const isFormValid =
    currentPassword.trim().length > 0 &&
    criteria.minLength &&
    criteria.hasUpper &&
    criteria.hasLower &&
    criteria.hasDigit &&
    criteria.hasSpecial &&
    criteria.differsFromCurrent &&
    criteria.noEmailLocalPart &&
    criteria.matchesConfirm;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);
    setSuccessMessage(null);

    if (!isFormValid) {
      setErrorMessage('Please ensure all password policy requirements are satisfied.');
      return;
    }

    setSubmitting(true);
    try {
      await changePassword({
        currentPassword,
        newPassword,
      });

      setSuccessMessage('Password changed successfully! All sessions have been invalidated.');

      // Clear local auth context and redirect to login with notification
      setTimeout(async () => {
        try {
          await logout();
        } catch {
          // ignore logout network errors as cookie is already cleared
        }
        navigate('/login', {
          replace: true,
          state: { message: 'Password changed successfully. Please sign in with your new password.' },
        });
      }, 1500);
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.data) {
        const apiError = err.response.data as ApiError;
        if (apiError.code === 'INVALID_CURRENT_PASSWORD') {
          setErrorMessage('The current password entered is incorrect.');
        } else if (apiError.code === 'NEW_PASSWORD_SAME_AS_CURRENT') {
          setErrorMessage('The new password must be different from your current password.');
        } else if (apiError.code === 'SOCIAL_ACCOUNT_PASSWORD_NOT_SUPPORTED') {
          setErrorMessage('Social login accounts cannot change password.');
        } else {
          setErrorMessage(apiError.detail || 'Failed to update password.');
        }
      } else {
        setErrorMessage('A network error occurred. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sd-change-pw-container">
      <div className="sd-change-pw-card">
        <header className="sd-change-pw-header">
          <h1 className="sd-change-pw-title">
            {isForcedChange ? 'Set New Password' : 'Change Password'}
          </h1>
          <p className="sd-change-pw-subtitle">
            {isForcedChange
              ? 'Your account requires an immediate password update before continuing.'
              : 'Choose a strong, unique password to protect your account.'}
          </p>
        </header>

        {isForcedChange && (
          <div className="sd-pw-banner-warning" role="alert">
            <span aria-hidden="true">⚠️</span>
            <div>
              <strong>Password Change Required:</strong> A temporary password was set on your account. You must choose a new permanent password to access platform features.
            </div>
          </div>
        )}

        {isGoogleAccount ? (
          <div className="sd-pw-banner-google" role="status">
            <h3>Google Sign-In Account</h3>
            <p>
              Your account is signed in through Google OAuth 2.0. Passwords and security credentials are managed directly by Google.
            </p>
            <div style={{ marginTop: '1rem' }}>
              <Link to="/profile" className="sd-btn-primary" style={{ textDecoration: 'none' }}>
                Return to Profile
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} noValidate>
            {successMessage && (
              <div className="sd-alert-success" role="status">
                ✓ {successMessage}
              </div>
            )}

            {errorMessage && (
              <div className="sd-alert-error" role="alert">
                {errorMessage}
              </div>
            )}

            {/* Current Password */}
            <div className="sd-field-group">
              <label htmlFor="currentPassword" className="sd-field-label">Current Password *</label>
              <div className="sd-pw-input-wrapper">
                <input
                  id="currentPassword"
                  type={showCurrentPassword ? 'text' : 'password'}
                  className="sd-input-text"
                  value={currentPassword}
                  onChange={e => setCurrentPassword(e.target.value)}
                  disabled={submitting || Boolean(successMessage)}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="sd-pw-toggle-btn"
                  onClick={() => setShowCurrentPassword(prev => !prev)}
                  tabIndex={-1}
                  aria-label={showCurrentPassword ? 'Hide current password' : 'Show current password'}
                >
                  {showCurrentPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>

            {/* New Password */}
            <div className="sd-field-group">
              <label htmlFor="newPassword" className="sd-field-label">New Password *</label>
              <div className="sd-pw-input-wrapper">
                <input
                  id="newPassword"
                  type={showNewPassword ? 'text' : 'password'}
                  className="sd-input-text"
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  disabled={submitting || Boolean(successMessage)}
                  maxLength={64}
                  required
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="sd-pw-toggle-btn"
                  onClick={() => setShowNewPassword(prev => !prev)}
                  tabIndex={-1}
                  aria-label={showNewPassword ? 'Hide new password' : 'Show new password'}
                >
                  {showNewPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>

            {/* Confirm New Password */}
            <div className="sd-field-group">
              <label htmlFor="confirmPassword" className="sd-field-label">Confirm New Password *</label>
              <div className="sd-pw-input-wrapper">
                <input
                  id="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className="sd-input-text"
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  disabled={submitting || Boolean(successMessage)}
                  maxLength={64}
                  required
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="sd-pw-toggle-btn"
                  onClick={() => setShowConfirmPassword(prev => !prev)}
                  tabIndex={-1}
                  aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
                >
                  {showConfirmPassword ? 'Hide' : 'Show'}
                </button>
              </div>
            </div>

            {/* Real-time Checklist */}
            <div className="sd-pw-checklist" aria-live="polite">
              <div className="sd-checklist-title">Password Requirements (SRS v2.1)</div>
              <ul className="sd-checklist-items">
                <li className={`sd-checklist-item ${criteria.minLength ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.minLength ? '✓' : '○'}</span>
                  10 to 64 characters
                </li>
                <li className={`sd-checklist-item ${criteria.hasUpper ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.hasUpper ? '✓' : '○'}</span>
                  At least one uppercase letter (A-Z)
                </li>
                <li className={`sd-checklist-item ${criteria.hasLower ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.hasLower ? '✓' : '○'}</span>
                  At least one lowercase letter (a-z)
                </li>
                <li className={`sd-checklist-item ${criteria.hasDigit ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.hasDigit ? '✓' : '○'}</span>
                  At least one digit (0-9)
                </li>
                <li className={`sd-checklist-item ${criteria.hasSpecial ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.hasSpecial ? '✓' : '○'}</span>
                  At least one special character
                </li>
                <li className={`sd-checklist-item ${criteria.differsFromCurrent ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.differsFromCurrent ? '✓' : '○'}</span>
                  Different from current password
                </li>
                {emailLocalPart && (
                  <li className={`sd-checklist-item ${criteria.noEmailLocalPart ? 'valid' : 'invalid'}`}>
                    <span className="sd-check-icon">{criteria.noEmailLocalPart ? '✓' : '○'}</span>
                    Does not contain your email username
                  </li>
                )}
                <li className={`sd-checklist-item ${criteria.matchesConfirm ? 'valid' : 'invalid'}`}>
                  <span className="sd-check-icon">{criteria.matchesConfirm ? '✓' : '○'}</span>
                  Passwords match
                </li>
              </ul>
            </div>

            <div className="sd-button-group" style={{ flexDirection: 'column' }}>
              <button
                type="submit"
                className="sd-btn-primary"
                style={{ width: '100%', padding: '0.8rem' }}
                disabled={submitting || !isFormValid || Boolean(successMessage)}
              >
                {submitting ? 'Updating password…' : 'Update Password'}
              </button>

              {!isForcedChange && (
                <Link
                  to="/profile"
                  className="sd-btn-secondary"
                  style={{ width: '100%', textAlign: 'center', textDecoration: 'none', boxSizing: 'border-box' }}
                >
                  Cancel
                </Link>
              )}
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
