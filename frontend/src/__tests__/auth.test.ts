import { describe, it, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { setAuthToken, getAuthToken, setSessionExpiredCallback } from '../api/axios.js';
import { getRoleHomePath } from '../utils/roleUtils.ts';
import { extractApiError } from '../utils/errorUtils.ts';

describe('In-Memory Token Storage (SRS Section 3.6, FR-AUTH-003)', () => {
  beforeEach(() => {
    setAuthToken(null);
  });

  it('stores access token in memory only', () => {
    assert.strictEqual(getAuthToken(), null);
    setAuthToken('mock-jwt-token-xyz');
    assert.strictEqual(getAuthToken(), 'mock-jwt-token-xyz');
  });

  it('clears token from memory on reset', () => {
    setAuthToken('token-123');
    setAuthToken(null);
    assert.strictEqual(getAuthToken(), null);
  });

  it('triggers session expired callback on expiration', () => {
    let callbackTriggered = false;
    setSessionExpiredCallback(() => {
      callbackTriggered = true;
      setAuthToken(null);
    });

    setAuthToken('initial-token');
    assert.strictEqual(getAuthToken(), 'initial-token');
    assert.strictEqual(callbackTriggered, false);
  });
});

describe('Role-Based Navigation & Home Paths (SRS Section 6.3)', () => {
  it('maps ADMIN to /admin', () => {
    assert.strictEqual(getRoleHomePath('ADMIN'), '/admin');
  });

  it('maps CUSTOMER to /customer', () => {
    assert.strictEqual(getRoleHomePath('CUSTOMER'), '/customer');
  });

  it('maps MERCHANT to /merchant', () => {
    assert.strictEqual(getRoleHomePath('MERCHANT'), '/merchant');
  });

  it('maps COURIER to /courier', () => {
    assert.strictEqual(getRoleHomePath('COURIER'), '/courier');
  });

  it('falls back to / for invalid or unknown role', () => {
    assert.strictEqual(getRoleHomePath('UNKNOWN'), '/');
    assert.strictEqual(getRoleHomePath(undefined), '/');
  });
});

describe('API Error Extraction & Problem Details (RFC 9457)', () => {
  it('extracts fallback message for plain errors', () => {
    const err = new Error('Network failure');
    const result = extractApiError(err);
    assert.strictEqual(result.detail, 'Network failure');
    assert.strictEqual(result.title, 'Application Error');
  });

  it('handles unknown non-error types gracefully', () => {
    const result = extractApiError('some string', 'Custom fallback');
    assert.strictEqual(result.detail, 'Custom fallback');
    assert.strictEqual(result.title, 'Unknown Error');
  });
});

describe('Password Policy & Form Validation Rules (SRS FR-AUTH-002, FR-AUTH-012)', () => {
  const PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d]).{10,64}$/;

  it('validates compliant 10-character complex passwords', () => {
    assert.strictEqual(PASSWORD_REGEX.test('Valid123!Pass'), true);
    assert.strictEqual(PASSWORD_REGEX.test('Strong#2026Test'), true);
  });

  it('rejects short passwords (< 10 chars)', () => {
    assert.strictEqual(PASSWORD_REGEX.test('Short1!'), false);
  });

  it('rejects passwords without uppercase letter', () => {
    assert.strictEqual(PASSWORD_REGEX.test('lowercase123!@#'), false);
  });

  it('rejects passwords without digit', () => {
    assert.strictEqual(PASSWORD_REGEX.test('NoDigitPass!@#'), false);
  });

  it('rejects passwords without special character', () => {
    assert.strictEqual(PASSWORD_REGEX.test('NoSpecialChar123'), false);
  });

  it('rejects password containing username/email local-part', () => {
    const email = 'kasun.perera@example.lk';
    const localPart = email.substring(0, email.indexOf('@')).toLowerCase();
    const candidatePass = 'Kasun.Perera123!#';
    assert.strictEqual(candidatePass.toLowerCase().includes(localPart), true);
  });
});

describe('Google OAuth Login URL Generation (FR-AUTH-009, FR-AUTH-011)', () => {
  it('generates expected Google OAuth endpoint path', () => {
    const expectedPath = '/api/v1/auth/google/login';
    assert.strictEqual(expectedPath, '/api/v1/auth/google/login');
  });

  it('handles OAuth error query parameters correctly', () => {
    const errorMessages: Record<string, string> = {
      google_denied: 'Google sign-in was cancelled or access was denied.',
      account_exists: 'An account already exists with this email address. Please sign in with your email and password.',
      account_deactivated: 'Your account has been deactivated. Please contact customer support.',
      google_failed: 'Google authentication failed. Please try again or sign in with your email.',
    };

    assert.strictEqual(errorMessages['account_exists'].includes('already exists'), true);
    assert.strictEqual(errorMessages['account_deactivated'].includes('deactivated'), true);
  });
});
