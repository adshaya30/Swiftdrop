import { useEffect, useRef } from 'react';

/**
 * Thin adapter (member prompt #6, SRS 7.6, master 8.1) to the tracking pieces of #9: api/tracking.js `getTimeline`,
 * pages/public/components/ParcelTimeline.jsx and pages/public/hooks/useTrackingStream.js. `import.meta.glob` is an
 * empty object while a file does not exist, so the build never breaks and each piece is used once it is merged.
 */
const first = (modules) => Object.values(modules)[0];

const trackingApi = first(import.meta.glob('/src/api/tracking.js', { eager: true }));
const timelineModule = first(import.meta.glob('/src/pages/public/components/ParcelTimeline.jsx', { eager: true }));
const streamModule = first(import.meta.glob('/src/pages/public/hooks/useTrackingStream.js', { eager: true }));

export const getTimeline = trackingApi?.getTimeline;
export const ParcelTimeline = timelineModule?.default ?? timelineModule?.ParcelTimeline;

const streamHook = typeof streamModule?.useTrackingStream === 'function' ? streamModule.useTrackingStream : null;
export const hasTrackingStream = Boolean(streamHook);

/**
 * Calls onChange whenever the live view reports a new status or update time (not on the first value).
 * Exported for tests; the detail page uses useStatusChanges.
 */
export function useOnStatusChange(view, onChange) {
  const callback = useRef(onChange);
  callback.current = onChange;
  const last = useRef(null);
  const key = view?.status ? `${view.status}|${view.updatedAt ?? ''}` : null;

  useEffect(() => {
    if (!key) return;
    if (last.current !== null && last.current !== key) {
      callback.current?.();
    }
    last.current = key;
  }, [key]);
}

/**
 * FR-UI-007: refetch the page's data on every SSE status event or polling result. The stream hook is chosen once at
 * module load, so the hook order never changes between renders.
 */
export function useStatusChanges(trackingNumber, onChange) {
  const live = streamHook ? streamHook(trackingNumber || null, { onStatus: onChange }) : null;
  useOnStatusChange(live?.data, onChange);
}
