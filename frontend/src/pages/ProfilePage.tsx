import { useState, useEffect, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { getProfile, updateProfile, type UserProfile, type ApiError } from '../services/authService';
import LoadingSpinner from '../components/LoadingSpinner';
import './ProfilePage.css';

interface EditFormState {
  fullName: string;
  phone: string;
}

interface FormErrors {
  fullName?: string;
  phone?: string;
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState<EditFormState>({ fullName: '', phone: '' });
  const [errors, setErrors] = useState<FormErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    loadProfile();
  }, []);

  const loadProfile = async () => {
    setLoading(true);
    setGeneralError(null);
    try {
      const data = await getProfile();
      setProfile(data);
      setEditForm({
        fullName: data.fullName,
        phone: data.phone || '',
      });
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.data) {
        const apiError = err.response.data as ApiError;
        setGeneralError(apiError.detail || 'Failed to load profile.');
      } else {
        setGeneralError('An unexpected error occurred while loading your profile.');
      }
    } finally {
      setLoading(false);
    }
  };

  const validate = (): boolean => {
    const newErrors: FormErrors = {};

    if (!editForm.fullName.trim()) {
      newErrors.fullName = 'Full name is required.';
    } else if (editForm.fullName.trim().length < 2 || editForm.fullName.trim().length > 100) {
      newErrors.fullName = 'Full name must be between 2 and 100 characters.';
    }

    if (editForm.phone.trim()) {
      const lkPhoneRegex = /^\+94[0-9]{9}$/;
      if (!lkPhoneRegex.test(editForm.phone.trim())) {
        newErrors.phone = 'Phone number must be in Sri Lankan format (+94XXXXXXXXX).';
      }
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSave = async (e: FormEvent) => {
    e.preventDefault();
    setSuccessMessage(null);
    setGeneralError(null);

    if (!validate()) {
      return;
    }

    setSaving(true);
    try {
      const updated = await updateProfile({
        fullName: editForm.fullName.trim(),
        phone: editForm.phone.trim() || null,
      });
      setProfile(updated);
      setIsEditing(false);
      setSuccessMessage('Your profile has been updated successfully.');
    } catch (err: unknown) {
      if (axios.isAxiosError(err) && err.response?.data) {
        const apiError = err.response.data as ApiError;
        setGeneralError(apiError.detail || 'Failed to update profile.');
      } else {
        setGeneralError('An unexpected network error occurred.');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    if (profile) {
      setEditForm({
        fullName: profile.fullName,
        phone: profile.phone || '',
      });
    }
    setErrors({});
    setIsEditing(false);
  };

  if (loading) {
    return <LoadingSpinner fullScreen label="Loading profile details…" />;
  }

  if (!profile) {
    return (
      <div className="sd-profile-container">
        <div className="sd-alert-error" role="alert">
          {generalError || 'Unable to retrieve user profile.'}
        </div>
        <button type="button" className="sd-btn-primary" onClick={loadProfile}>
          Retry
        </button>
      </div>
    );
  }

  const isGoogle = profile.authProvider === 'GOOGLE';
  const roleLower = profile.role.toLowerCase();

  return (
    <div className="sd-profile-container">
      <header className="sd-profile-header">
        <h1 className="sd-profile-title">Account Profile</h1>
        <p className="sd-profile-subtitle">View and update your personal information and security credentials.</p>
      </header>

      {successMessage && (
        <div className="sd-alert-success" role="status">
          ✓ {successMessage}
        </div>
      )}

      {generalError && (
        <div className="sd-alert-error" role="alert">
          {generalError}
        </div>
      )}

      {profile.mustChangePassword && (
        <div className="sd-alert-error" role="alert" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>⚠️ <strong>Password change required:</strong> Please update your temporary password to regain full access.</span>
          <Link to="/change-password" className="sd-btn-primary" style={{ textDecoration: 'none', padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>
            Change Password
          </Link>
        </div>
      )}

      {/* Main Profile Info Card */}
      <section className="sd-profile-card">
        <div className="sd-profile-hero">
          <div className="sd-profile-avatar" aria-hidden="true">
            {profile.fullName.charAt(0).toUpperCase()}
          </div>
          <div className="sd-profile-identity">
            <h2 className="sd-profile-name">{profile.fullName}</h2>
            <div className="sd-profile-badges">
              <span className={`sd-badge sd-badge-role-${roleLower}`}>
                {profile.role}
              </span>
              <span className={`sd-badge ${isGoogle ? 'sd-badge-provider-google' : 'sd-badge-provider-local'}`}>
                {isGoogle ? 'Google Account' : 'Local Account'}
              </span>
            </div>
          </div>
        </div>

        {/* Profile Details or Edit Form */}
        {!isEditing ? (
          <div>
            <div className="sd-grid-details">
              <div className="sd-detail-item">
                <span className="sd-detail-label">User ID</span>
                <span className="sd-detail-value" style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {profile.id}
                </span>
              </div>
              <div className="sd-detail-item">
                <span className="sd-detail-label">Email Address</span>
                <span className="sd-detail-value">{profile.email}</span>
              </div>
              <div className="sd-detail-item">
                <span className="sd-detail-label">Phone Number</span>
                <span className="sd-detail-value">{profile.phone || 'Not provided'}</span>
              </div>
              <div className="sd-detail-item">
                <span className="sd-detail-label">Account Created</span>
                <span className="sd-detail-value">
                  {new Date(profile.createdAt).toLocaleDateString(undefined, {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                </span>
              </div>
            </div>

            {profile.merchant && (
              <div className="sd-merchant-section">
                <h3 className="sd-section-title">Merchant Details</h3>
                <div className="sd-grid-details">
                  <div className="sd-detail-item">
                    <span className="sd-detail-label">Business Name</span>
                    <span className="sd-detail-value">{profile.merchant.businessName}</span>
                  </div>
                  <div className="sd-detail-item">
                    <span className="sd-detail-label">Merchant Status</span>
                    <span className="sd-detail-value">{profile.merchant.status}</span>
                  </div>
                  <div className="sd-detail-item">
                    <span className="sd-detail-label">Discount Rate</span>
                    <span className="sd-detail-value">{profile.merchant.discountPercent}%</span>
                  </div>
                </div>
              </div>
            )}

            <div className="sd-button-group">
              <button
                type="button"
                className="sd-btn-primary"
                onClick={() => setIsEditing(true)}
              >
                Edit Profile
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSave} noValidate>
            <div className="sd-field-group">
              <label htmlFor="fullName" className="sd-field-label">Full Name *</label>
              <input
                id="fullName"
                type="text"
                className="sd-input-text"
                value={editForm.fullName}
                onChange={e => setEditForm({ ...editForm, fullName: e.target.value })}
                disabled={saving}
                maxLength={100}
                required
              />
              {errors.fullName && <div className="sd-field-error">{errors.fullName}</div>}
              <div className="sd-field-hint">Between 2 and 100 characters.</div>
            </div>

            <div className="sd-field-group">
              <label htmlFor="phone" className="sd-field-label">Phone Number</label>
              <input
                id="phone"
                type="tel"
                className="sd-input-text"
                value={editForm.phone}
                onChange={e => setEditForm({ ...editForm, phone: e.target.value })}
                placeholder="+94771234567"
                disabled={saving}
              />
              {errors.phone && <div className="sd-field-error">{errors.phone}</div>}
              <div className="sd-field-hint">Sri Lankan E.164 format (+94XXXXXXXXX) or leave blank.</div>
            </div>

            <div className="sd-field-group">
              <label className="sd-field-label">Email Address (Read-only)</label>
              <input
                type="text"
                className="sd-input-text"
                value={profile.email}
                disabled
              />
              <div className="sd-field-hint">Email address cannot be modified after registration.</div>
            </div>

            <div className="sd-button-group">
              <button
                type="submit"
                className="sd-btn-primary"
                disabled={saving}
              >
                {saving ? 'Saving changes…' : 'Save Changes'}
              </button>
              <button
                type="button"
                className="sd-btn-secondary"
                onClick={handleCancel}
                disabled={saving}
              >
                Cancel
              </button>
            </div>
          </form>
        )}
      </section>

      {/* Security Credentials Card */}
      <section className="sd-profile-card">
        <h3 className="sd-section-title">Security &amp; Authentication</h3>

        {isGoogle ? (
          <div className="sd-security-action">
            <div className="sd-security-info">
              <h4>Single Sign-On (Google OAuth)</h4>
              <p>Your account is linked with Google. Password changes and 2-factor authentication are managed through your Google Account settings.</p>
            </div>
          </div>
        ) : (
          <div className="sd-security-action">
            <div className="sd-security-info">
              <h4>Account Password</h4>
              <p>Regularly updating your password keeps your SwiftDrop account secure.</p>
            </div>
            <Link to="/change-password" className="sd-btn-secondary" style={{ textDecoration: 'none' }}>
              Change Password
            </Link>
          </div>
        )}
      </section>
    </div>
  );
}
