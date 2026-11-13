import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getPublicTracking,
  isTerminalStatus,
  publicTrackingQueryKey,
  publicTrackingStreamUrl,
  toPublicTrackingView,
} from '../../../services/trackingService';

const FAILURES_BEFORE_POLL = 3;
const POLL_INTERVAL_MS = 15000;
const SSE_RETRY_MS = 120000;

/**
 * Live public tracking (SRS 3.8, FR-UI-006).
 * Opens the tracking SSE stream, applies snapshot and status payloads,
 * and falls back to polling after three consecutive stream failures.
 * The EventSource is closed on unmount, when the tracking number changes,
 * and immediately after a terminal status.
 */
export function useTrackingStream(trackingNumber) {
  const queryClient = useQueryClient();
  const [connectionMode, setConnectionMode] = useState('live');

  const query = useQuery({
    queryKey: publicTrackingQueryKey(trackingNumber || ''),
    queryFn: () => getPublicTracking(trackingNumber),
    enabled: Boolean(trackingNumber),
    retry: false,
  });

  useEffect(() => {
    if (!trackingNumber || !query.isSuccess) {
      return undefined;
    }

    const initial = queryClient.getQueryData(publicTrackingQueryKey(trackingNumber));
    if (initial && isTerminalStatus(initial.status)) {
      return undefined;
    }

    const handles = {
      source: null,
      pollTimer: null,
      retryTimer: null,
      stopped: false,
      ignoreErrors: false,
      mode: 'live',
    };

    const clearTimers = () => {
      if (handles.pollTimer) {
        clearInterval(handles.pollTimer);
        handles.pollTimer = null;
      }
      if (handles.retryTimer) {
        clearTimeout(handles.retryTimer);
        handles.retryTimer = null;
      }
    };

    const publish = (view) => {
      queryClient.setQueryData(publicTrackingQueryKey(trackingNumber), view);
      if (isTerminalStatus(view.status)) {
        handles.stopped = true;
        handles.ignoreErrors = true;
        handles.source?.close();
        handles.source = null;
        clearTimers();
      }
    };

    const pollOnce = async () => {
      if (handles.stopped) return;
      try {
        const view = await getPublicTracking(trackingNumber);
        publish(view);
      } catch {
        // Keep the last public view when a poll fails.
      }
    };

    const openStream = () => {
      if (handles.stopped) return;
      handles.ignoreErrors = false;
      handles.mode = 'live';
      setConnectionMode('live');
      let failures = 0;
      const source = new EventSource(publicTrackingStreamUrl(trackingNumber));
      handles.source = source;

      const onPayload = (event) => {
        if (handles.stopped || handles.source !== source) return;
        failures = 0;
        try {
          publish(toPublicTrackingView(JSON.parse(event.data)));
        } catch {
          // Ignore a malformed SSE frame.
        }
      };

      source.addEventListener('snapshot', onPayload);
      source.addEventListener('status', onPayload);
      source.onerror = () => {
        if (handles.stopped || handles.ignoreErrors || handles.source !== source) return;
        failures += 1;
        if (failures < FAILURES_BEFORE_POLL) return;
        handles.ignoreErrors = true;
        source.close();
        if (handles.source === source) handles.source = null;
        handles.mode = 'polling';
        setConnectionMode('polling');
        clearTimers();
        handles.pollTimer = setInterval(() => {
          void pollOnce();
        }, POLL_INTERVAL_MS);
        handles.retryTimer = setTimeout(() => {
          if (handles.stopped) return;
          clearTimers();
          openStream();
        }, SSE_RETRY_MS);
      };
    };

    openStream();

    return () => {
      handles.stopped = true;
      handles.ignoreErrors = true;
      handles.source?.close();
      handles.source = null;
      clearTimers();
    };
  }, [trackingNumber, query.isSuccess, queryClient]);

  return {
    data: query.data,
    isLoading: query.isPending && !query.data,
    error: query.error,
    connectionMode,
    refetch: query.refetch,
  };
}

export default useTrackingStream;
