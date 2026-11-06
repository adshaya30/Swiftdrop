/**
 * SwiftDrop HTTP client adhering to SWIFTDROP_MASTER_PROMPT Section 8.1 and Section 9.
 *
 * Rules:
 * - Access token is held STRICTLY in module-level memory (never localStorage, sessionStorage, or cookies).
 * - Single-flight refresh mutex on 401 TOKEN_EXPIRED with retry-once.
 * - 403 PASSWORD_CHANGE_REQUIRED redirect to /change-password.
 * - Relative URLs with VITE_API_BASE_URL prefix.
 */

let moduleAccessToken = null;
let refreshPromise = null;

const getBaseUrl = () => {
  return (import.meta.env && import.meta.env.VITE_API_BASE_URL) || '';
};

export class ApiError extends Error {
  constructor({ status, code = 'API_ERROR', detail = 'An unexpected error occurred', errors = [], traceId = null }) {
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.errors = errors;
    this.traceId = traceId;
  }
}

export function setAccessToken(token) {
  moduleAccessToken = token || null;
}

export function getAccessToken() {
  return moduleAccessToken;
}

export function clearAccessToken() {
  moduleAccessToken = null;
}

/**
 * Executes a single-flight token refresh against POST /api/v1/auth/refresh.
 */
async function executeSingleFlightRefresh() {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    try {
      const baseUrl = getBaseUrl();
      const res = await fetch(`${baseUrl}/api/v1/auth/refresh`, {
        method: 'POST',
        headers: {
          'X-Requested-With': 'SwiftDrop',
          'Accept': 'application/json',
        },
        credentials: 'include',
      });

      if (!res.ok) {
        clearAccessToken();
        if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
          window.location.href = '/login';
        }
        throw new ApiError({
          status: res.status,
          code: 'REFRESH_FAILED',
          detail: 'Session expired. Please log in again.',
        });
      }

      const data = await res.json();
      setAccessToken(data.accessToken);
      return data.accessToken;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

/**
 * Standard API request wrapper.
 */
export async function apiRequest(method, path, { body, query, headers = {}, idempotencyKey } = {}) {
  let url = `${getBaseUrl()}${path}`;
  if (query && Object.keys(query).length > 0) {
    const params = new URLSearchParams();
    for (const [key, val] of Object.entries(query)) {
      if (val !== undefined && val !== null) {
        params.append(key, String(val));
      }
    }
    const qStr = params.toString();
    if (qStr) {
      url += (url.includes('?') ? '&' : '?') + qStr;
    }
  }

  const buildHeaders = () => {
    const h = {
      'Accept': 'application/json',
      ...headers,
    };
    if (body !== undefined && !(body instanceof FormData)) {
      h['Content-Type'] = 'application/json';
    }
    if (moduleAccessToken) {
      h['Authorization'] = `Bearer ${moduleAccessToken}`;
    }
    if (idempotencyKey) {
      h['Idempotency-Key'] = idempotencyKey;
    }
    return h;
  };

  const reqOptions = {
    method: method.toUpperCase(),
    headers: buildHeaders(),
    credentials: 'include',
  };

  if (body !== undefined) {
    reqOptions.body = body instanceof FormData ? body : JSON.stringify(body);
  }

  let response = await fetch(url, reqOptions);

  // Check 401 for token expiry & retry-once
  if (response.status === 401) {
    const clone = response.clone();
    let errJson = null;
    try {
      errJson = await clone.json();
    } catch {
      // ignore
    }

    // Attempt refresh on 401
    try {
      const newToken = await executeSingleFlightRefresh();
      // Retry once with new token
      reqOptions.headers['Authorization'] = `Bearer ${newToken}`;
      response = await fetch(url, reqOptions);
    } catch (refreshErr) {
      throw errJson ? parseApiProblem(response.status, errJson) : refreshErr;
    }
  }

  // Handle 403 PASSWORD_CHANGE_REQUIRED
  if (response.status === 403) {
    const clone = response.clone();
    try {
      const json = await clone.json();
      if (json.code === 'PASSWORD_CHANGE_REQUIRED') {
        if (typeof window !== 'undefined' && window.location.pathname !== '/change-password') {
          window.location.href = '/change-password';
        }
      }
    } catch {
      // ignore
    }
  }

  if (!response.ok) {
    let errorData = null;
    try {
      errorData = await response.json();
    } catch {
      // not JSON
    }
    throw parseApiProblem(response.status, errorData);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

/**
 * Multipart FormData upload wrapper (FR-UI-011).
 */
export async function apiForm(path, formData, { headers = {} } = {}) {
  return apiRequest('POST', path, {
    body: formData,
    headers,
  });
}

/**
 * Binary blob download wrapper using Bearer authorization.
 */
export async function apiBlob(path, { headers = {} } = {}) {
  const url = `${getBaseUrl()}${path}`;
  const reqHeaders = {
    ...headers,
  };
  if (moduleAccessToken) {
    reqHeaders['Authorization'] = `Bearer ${moduleAccessToken}`;
  }

  const response = await fetch(url, {
    method: 'GET',
    headers: reqHeaders,
    credentials: 'include',
  });

  if (!response.ok) {
    let errorData = null;
    try {
      errorData = await response.json();
    } catch {
      // ignore
    }
    throw parseApiProblem(response.status, errorData);
  }

  return response.blob();
}

function parseApiProblem(status, data) {
  if (data && typeof data === 'object') {
    return new ApiError({
      status,
      code: data.code || data.error || `HTTP_${status}`,
      detail: data.detail || data.error_description || data.message || 'Request failed',
      errors: Array.isArray(data.errors) ? data.errors : [],
      traceId: data.traceId || null,
    });
  }
  return new ApiError({
    status,
    code: `HTTP_${status}`,
    detail: `HTTP request failed with status ${status}`,
  });
}
