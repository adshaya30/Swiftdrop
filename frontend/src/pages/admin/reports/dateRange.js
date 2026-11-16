/**
 * Date-range rules of FR-PAR-013, mirrored from the server: from/to are yyyy-MM-dd dates in Asia/Colombo, inclusive;
 * the default range is the last 30 days (today and the 29 days before it); the maximum is 366 days.
 */
export const DEFAULT_DAYS = 30;
export const MAX_DAYS = 366;

const MS_PER_DAY = 24 * 60 * 60 * 1000;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Today's date in Asia/Colombo as yyyy-MM-dd. */
export function todayColombo(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now);
  const get = (type) => parts.find((p) => p.type === type).value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}

function toUtcMs(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

export function addDays(isoDate, days) {
  return new Date(toUtcMs(isoDate) + days * MS_PER_DAY).toISOString().slice(0, 10);
}

/** Number of days in the inclusive range. */
export function daysInclusive(from, to) {
  return Math.round((toUtcMs(to) - toUtcMs(from)) / MS_PER_DAY) + 1;
}

export function defaultRange(now = new Date()) {
  const to = todayColombo(now);
  return { from: addDays(to, -(DEFAULT_DAYS - 1)), to };
}

/** Returns an error message, or null when the range is acceptable. */
export function validateRange(from, to) {
  if (!ISO_DATE.test(from || '') || !ISO_DATE.test(to || '')) {
    return 'Choose both a from date and a to date.';
  }
  if (from > to) {
    return 'The "from" date must not be after the "to" date.';
  }
  if (daysInclusive(from, to) > MAX_DAYS) {
    return `The range can be at most ${MAX_DAYS} days.`;
  }
  return null;
}
