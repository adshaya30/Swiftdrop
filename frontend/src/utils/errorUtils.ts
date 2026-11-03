import axios from 'axios';
import type { ApiError } from '../services/authService';

export interface FormattedError {
  status?: number;
  title: string;
  detail: string;
  fieldErrors: Record<string, string>;
  traceId?: string;
}

/**
 * Normalizes backend RFC 9457 Problem Details and Axios errors into a consistent format.
 */
export function extractApiError(error: unknown, fallbackMessage = 'An unexpected error occurred.'): FormattedError {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiError | undefined;
    const status = error.response?.status;

    const fieldErrors: Record<string, string> = {};
    if (data?.errors && Array.isArray(data.errors)) {
      data.errors.forEach(fe => {
        if (fe.field && fe.message) {
          fieldErrors[fe.field] = fe.message;
        }
      });
    }

    let detail = data?.detail || error.message || fallbackMessage;
    if (status === 429) {
      const retryAfter = error.response?.headers?.['retry-after'];
      if (retryAfter) {
        const mins = Math.ceil(parseInt(retryAfter, 10) / 60);
        detail = `Account is temporarily locked. Please try again in ${mins} minute${mins > 1 ? 's' : ''}.`;
      }
    } else if (status === 401) {
      detail = data?.detail || 'Invalid credentials or expired session.';
    } else if (status === 403) {
      detail = data?.detail || 'Access denied. You do not have permission to view this resource.';
    }

    return {
      status,
      title: data?.title || (status ? `Error ${status}` : 'Request Error'),
      detail,
      fieldErrors,
      traceId: data?.traceId,
    };
  }

  if (error instanceof Error) {
    return {
      title: 'Application Error',
      detail: error.message,
      fieldErrors: {},
    };
  }

  return {
    title: 'Unknown Error',
    detail: fallbackMessage,
    fieldErrors: {},
  };
}

