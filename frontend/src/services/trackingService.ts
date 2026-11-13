import api from '../api/axios';

/**
 * Public tracking view (FR-TRK-002, BR-20).
 * Only these fields are kept. Fees, contacts, notes, proof, courier identity
 * and merchant data are dropped even if a payload contains them.
 */
export interface PublicLocation {
  districtName: string;
  zoneName: string;
}

export interface PublicHistoryEntry {
  status: string;
  at: string;
  failureReasonLabel: string | null;
}

export interface PublicTracking {
  trackingNumber: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  origin: PublicLocation;
  destination: PublicLocation;
  history: PublicHistoryEntry[];
}

export type TrackingErrorKind = 'notFound' | 'network' | 'server';

export interface ClassifiedTrackingError {
  kind: TrackingErrorKind;
  detail: string;
}

const NOT_FOUND_MESSAGE = 'We could not find a parcel with that tracking number';

const TERMINAL_STATUSES = new Set(['DELIVERED', 'FAILED', 'CANCELLED']);

interface RawHistoryEntry {
  status?: string;
  at?: string;
  failureReasonLabel?: string | null;
}

interface RawPublicTracking {
  trackingNumber?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  origin?: { districtName?: string; zoneName?: string };
  destination?: { districtName?: string; zoneName?: string };
  history?: RawHistoryEntry[];
}

function eventTime(at: string): number {
  const time = Date.parse(at);
  return Number.isNaN(time) ? Number.MAX_SAFE_INTEGER : time;
}

export function isTerminalStatus(status: string | null | undefined): boolean {
  return TERMINAL_STATUSES.has(status || '');
}

export function publicTrackingQueryKey(trackingNumber: string) {
  return ['public-tracking', trackingNumber] as const;
}

/** Keeps the public contract and returns history oldest-first. */
export function toPublicTrackingView(payload: RawPublicTracking | null | undefined): PublicTracking {
  const history = Array.isArray(payload?.history) ? payload.history : [];
  return {
    trackingNumber: payload?.trackingNumber ?? '',
    status: payload?.status ?? '',
    createdAt: payload?.createdAt ?? '',
    updatedAt: payload?.updatedAt ?? '',
    origin: {
      districtName: payload?.origin?.districtName ?? '',
      zoneName: payload?.origin?.zoneName ?? '',
    },
    destination: {
      districtName: payload?.destination?.districtName ?? '',
      zoneName: payload?.destination?.zoneName ?? '',
    },
    history: history
      .map((entry) => ({
        status: entry?.status ?? '',
        at: entry?.at ?? '',
        failureReasonLabel: entry?.failureReasonLabel ?? null,
      }))
      .sort((left, right) => eventTime(left.at) - eventTime(right.at)),
  };
}

/** GET /api/v1/public/tracking/{trackingNumber} */
export async function getPublicTracking(trackingNumber: string): Promise<PublicTracking> {
  const response = await api.get<RawPublicTracking>(
    `/public/tracking/${encodeURIComponent(trackingNumber)}`,
  );
  return toPublicTrackingView(response.data);
}

/** GET /api/v1/public/tracking/{trackingNumber}/stream */
export function publicTrackingStreamUrl(trackingNumber: string): string {
  const base = String(api.defaults.baseURL || '/api/v1').replace(/\/+$/, '');
  return `${base}/public/tracking/${encodeURIComponent(trackingNumber)}/stream`;
}

export function classifyTrackingError(error: unknown): ClassifiedTrackingError {
  const response = (error as { response?: { status?: number; data?: { detail?: string } } } | null)?.response;
  const status = response?.status;
  if (status === 404) {
    return { kind: 'notFound', detail: NOT_FOUND_MESSAGE };
  }
  if (typeof status === 'number' && status >= 500) {
    return {
      kind: 'server',
      detail: response?.data?.detail || 'The tracking service is unavailable. Please try again.',
    };
  }
  if (typeof status === 'number') {
    return {
      kind: 'server',
      detail: response?.data?.detail || 'The tracking service could not complete your request.',
    };
  }
  return { kind: 'network', detail: 'Check your connection and try again.' };
}
