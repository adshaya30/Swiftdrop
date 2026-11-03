import api from '../api/axios';

export interface RegistrationPayload {
  fullName: string;
  email: string;
  phone: string;
  password: string;
}

export interface UserSummary {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  role: string;
  authProvider: string;
  status: string;
  createdAt: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface LoginUserSummary {
  id: string;
  fullName: string;
  role: string;
  mustChangePassword?: boolean;
  merchantStatus?: string;
  merchantId?: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: LoginUserSummary;
}

export interface AuthConfig {
  googleEnabled: boolean;
}

export interface ApiError {
  type: string;
  title: string;
  status: number;
  detail: string;
  code: string;
  traceId: string;
  timestamp: string;
  errors?: Array<{ field: string; message: string }>;
}

/**
 * POST /api/v1/auth/register
 * Registers a new customer account.
 * Does NOT return tokens (registration ≠ login).
 */
export async function registerCustomer(payload: RegistrationPayload): Promise<UserSummary> {
  const response = await api.post<UserSummary>('/auth/register', payload);
  return response.data;
}

/**
 * POST /api/v1/auth/login
 * Authenticates user credentials and issues RS256 JWT access token.
 * Refresh token is set in HttpOnly cookie 'sd_refresh'.
 */
export async function loginCustomer(payload: LoginPayload): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>('/auth/login', payload);
  return response.data;
}

/**
 * POST /api/v1/auth/refresh
 * Rotates the refresh token (from sd_refresh HttpOnly cookie) and returns a new access token.
 * Requires X-Requested-With: SwiftDrop header.
 */
export async function refreshToken(): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>(
    '/auth/refresh',
    {},
    {
      headers: {
        'X-Requested-With': 'SwiftDrop',
      },
    }
  );
  return response.data;
}

/**
 * POST /api/v1/auth/logout
 * Revokes the active refresh token family and clears the sd_refresh HttpOnly cookie.
 * Requires X-Requested-With: SwiftDrop header.
 */
export async function logoutCustomer(): Promise<void> {
  await api.post(
    '/auth/logout',
    {},
    {
      headers: {
        'X-Requested-With': 'SwiftDrop',
      },
    }
  );
}

/**
 * GET /api/v1/auth/config
 * Retrieves public auth configuration (FR-AUTH-011).
 */
export async function getAuthConfig(): Promise<AuthConfig> {
  const response = await api.get<AuthConfig>('/auth/config');
  return response.data;
}
