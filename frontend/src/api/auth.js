import { apiRequest, setAccessToken, clearAccessToken } from './http';

/**
 * Auth API services per SRS v2.1 Section 7.3 and Master Prompt Section 8.1.
 */

export async function login(credentials) {
  const result = await apiRequest('POST', '/api/v1/auth/login', {
    body: credentials,
  });
  if (result && result.accessToken) {
    setAccessToken(result.accessToken);
  }
  return result;
}

export async function register(userData) {
  return apiRequest('POST', '/api/v1/auth/register', {
    body: userData,
  });
}

export async function registerMerchant(merchantData) {
  return apiRequest('POST', '/api/v1/auth/register-merchant', {
    body: merchantData,
  });
}

export async function refresh() {
  const result = await apiRequest('POST', '/api/v1/auth/refresh', {
    headers: { 'X-Requested-With': 'SwiftDrop' },
  });
  if (result && result.accessToken) {
    setAccessToken(result.accessToken);
  }
  return result;
}

export async function logout() {
  try {
    await apiRequest('POST', '/api/v1/auth/logout', {
      headers: { 'X-Requested-With': 'SwiftDrop' },
    });
  } finally {
    clearAccessToken();
  }
}

export async function getAuthConfig() {
  return apiRequest('GET', '/api/v1/auth/config');
}
