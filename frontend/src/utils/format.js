/**
 * Formatting utilities for SwiftDrop.
 * Adheres strictly to Asia/Colombo timezone, 12-hour clock, and LKR currency formatting.
 */

const TIMEZONE = 'Asia/Colombo';

export function formatDateTime(isoString) {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return String(isoString);

    return new Intl.DateTimeFormat('en-LK', {
      timeZone: TIMEZONE,
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    }).format(date);
  } catch {
    return String(isoString);
  }
}

export function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return String(isoString);

    return new Intl.DateTimeFormat('en-LK', {
      timeZone: TIMEZONE,
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(date);
  } catch {
    return String(isoString);
  }
}

/**
 * Formats a monetary value into "LKR 1,234.00" without floating point distortion.
 */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '') {
    return 'LKR 0.00';
  }

  const str = String(value).trim();
  const parts = str.split('.');
  const intPart = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const decPart = parts.length > 1 ? parts[1].padEnd(2, '0').slice(0, 2) : '00';

  return `LKR ${intPart}.${decPart}`;
}

/**
 * Maps server RFC 7807 problem details errors[] array to React Hook Form setError fields.
 */
export function applyServerErrors(setError, apiError) {
  if (!apiError || !setError) return;

  if (Array.isArray(apiError.errors) && apiError.errors.length > 0) {
    apiError.errors.forEach((err) => {
      const field = err.field || err.property;
      const message = err.message || err.detail || 'Invalid field value';
      if (field) {
        setError(field, { type: 'server', message });
      }
    });
  } else if (apiError.detail) {
    setError('root.serverError', {
      type: 'server',
      message: apiError.detail,
    });
  }
}
