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
  const response = await api.post<UserSummary>('/api/v1/auth/register', payload);
  return response.data;
}
