import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../theme/theme';
import TrackDetailPage from '../pages/public/track-detail';
import TrackPage from '../pages/public/track';
import { publicTrackingQueryKey } from '../services/trackingService';
import { useTrackingStream } from '../pages/public/hooks/useTrackingStream';
import api from '../api/axios';

vi.mock('../api/axios', () => ({
  default: {
    get: vi.fn(),
    defaults: { baseURL: '/api/v1' },
  },
}));

const TRACKING_NUMBER = 'SD-7K3M9QX2TA';
const OTHER_NUMBER = 'SD-7K3M9QX2TB';

class MockEventSource {
  static instances = [];

  constructor(url) {
    this.url = url;
    this.listeners = {};
    this.closed = false;
    this.onerror = null;
    MockEventSource.instances.push(this);
  }

  addEventListener(type, handler) {
    this.listeners[type] = handler;
  }

  close() {
    this.closed = true;
  }

  emit(type, payload) {
    this.listeners[type]?.({ data: JSON.stringify(payload) });
  }
}

function publicView(overrides = {}) {
  return {
    trackingNumber: TRACKING_NUMBER,
    status: 'PICKED_UP',
    createdAt: '2026-11-12T04:00:00Z',
    updatedAt: '2026-11-12T06:00:00Z',
    origin: { districtName: 'Colombo', zoneName: 'Zone A' },
    destination: { districtName: 'Kandy', zoneName: 'Zone B' },
    history: [
      { status: 'PICKED_UP', at: '2026-11-12T06:00:00Z', failureReasonLabel: null },
      { status: 'CREATED', at: '2026-11-12T04:00:00Z', failureReasonLabel: null },
    ],
    fee: { total: 'LKR 500.00' },
    customerPhone: '0771234567',
    customerEmail: 'recipient@example.com',
    note: 'Leave at the side gate',
    courierFirstName: 'Kamal',
    proof: { recipientName: 'Nimal Perera', photoUrl: 'proof-photo' },
    merchantReference: 'merchant-secret',
    ...overrides,
  };
}

function renderDetail(number = TRACKING_NUMBER) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[`/track/${number}`]}>
          <Routes>
            <Route path="/track/:trackingNumber" element={<TrackDetailPage />} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

function StreamProbe({ value }) {
  useTrackingStream(value);
  return <div>probe {value}</div>;
}

function rejectWithStatus(status, detail) {
  const error = new Error(detail);
  error.response = { status, data: { status, detail } };
  return error;
}

beforeEach(() => {
  MockEventSource.instances = [];
  vi.stubGlobal('EventSource', MockEventSource);
  api.get.mockReset();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('TST-041 public tracking', () => {
  it('shows a loading state while the tracking request is in flight', () => {
    api.get.mockImplementation(() => new Promise(() => {}));
    renderDetail();
    expect(screen.getByText('Loading tracking details...')).toBeInTheDocument();
  });

  it('renders the public tracking response', async () => {
    api.get.mockResolvedValue({ data: publicView() });
    renderDetail();

    expect(await screen.findByRole('heading', { name: TRACKING_NUMBER })).toBeInTheDocument();
    expect(screen.getByText('Colombo')).toBeInTheDocument();
    expect(screen.getByText('Zone A')).toBeInTheDocument();
    expect(screen.getByText('Kandy')).toBeInTheDocument();
    expect(screen.getByText('Zone B')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Status: Picked Up.');
    expect(api.get).toHaveBeenCalledWith(`/public/tracking/${TRACKING_NUMBER}`);
  });

  it('renders the timeline in chronological order', async () => {
    api.get.mockResolvedValue({
      data: publicView({
        status: 'FAILED',
        history: [
          { status: 'FAILED', at: '2026-11-12T08:00:00Z', failureReasonLabel: 'Recipient not available' },
          { status: 'CREATED', at: '2026-11-12T04:00:00Z', failureReasonLabel: null },
          { status: 'PICKED_UP', at: '2026-11-12T06:00:00Z', failureReasonLabel: null },
        ],
      }),
    });
    renderDetail();

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveTextContent('Created');
    expect(items[1]).toHaveTextContent('Picked Up');
    expect(items[2]).toHaveTextContent('Failed');
    expect(items[2]).toHaveTextContent('Recipient not available');
    expect(items[0].compareDocumentPosition(items[1]) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('shows the same not-found message for an unknown tracking number', async () => {
    api.get.mockRejectedValue(rejectWithStatus(404, 'Parcel not found'));
    renderDetail('SD-NOTFOUND1');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'We could not find a parcel with that tracking number',
    );
    expect(MockEventSource.instances).toHaveLength(0);
  });

  it('shows a network error when the request cannot reach the server', async () => {
    api.get.mockRejectedValue(new Error('Network Error'));
    renderDetail();

    expect(await screen.findByText('Network error')).toBeInTheDocument();
    expect(screen.getByText('Check your connection and try again.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try Again' })).toBeInTheDocument();
  });

  it('shows a server error when tracking returns 500', async () => {
    api.get.mockRejectedValue(rejectWithStatus(500, 'Database unavailable'));
    renderDetail();

    expect(await screen.findByText('Server error')).toBeInTheDocument();
    expect(screen.getByText('Database unavailable')).toBeInTheDocument();
  });

  it('updates the page when a status event arrives and closes the stream on unmount', async () => {
    api.get.mockResolvedValue({ data: publicView() });
    const view = renderDetail();

    await screen.findByRole('heading', { name: TRACKING_NUMBER });
    await waitFor(() => expect(MockEventSource.instances).toHaveLength(1));
    const source = MockEventSource.instances[0];
    expect(source.url).toBe(`/api/v1/public/tracking/${TRACKING_NUMBER}/stream`);

    source.emit('snapshot', publicView({
      status: 'PICKED_UP',
      history: [
        { status: 'CREATED', at: '2026-11-12T04:00:00Z', failureReasonLabel: null },
        { status: 'PICKED_UP', at: '2026-11-12T06:00:00Z', failureReasonLabel: null },
      ],
    }));
    expect(await screen.findAllByRole('listitem')).toHaveLength(2);

    source.emit('status', publicView({
      status: 'OUT_FOR_DELIVERY',
      updatedAt: '2026-11-12T07:00:00Z',
      history: [
        { status: 'CREATED', at: '2026-11-12T04:00:00Z', failureReasonLabel: null },
        { status: 'PICKED_UP', at: '2026-11-12T06:00:00Z', failureReasonLabel: null },
        { status: 'OUT_FOR_DELIVERY', at: '2026-11-12T07:00:00Z', failureReasonLabel: null },
      ],
    }));

    expect(await screen.findByText(/Status: Out for Delivery/)).toBeInTheDocument();
    const items = screen.getAllByRole('listitem');
    expect(items[2]).toHaveTextContent('Out for Delivery');

    view.unmount();
    expect(source.closed).toBe(true);
  });

  it('closes the previous SSE connection when the tracking number changes', async () => {
    api.get.mockImplementation((url) => {
      const number = String(url).includes(OTHER_NUMBER) ? OTHER_NUMBER : TRACKING_NUMBER;
      return Promise.resolve({
        data: publicView({
          trackingNumber: number,
          status: 'CREATED',
          history: [
            { status: 'CREATED', at: '2026-11-12T04:00:00Z', failureReasonLabel: null },
          ],
        }),
      });
    });

    const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const view = render(
      <QueryClientProvider client={client}>
        <ThemeProvider theme={theme}>
          <StreamProbe value={TRACKING_NUMBER} />
        </ThemeProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(MockEventSource.instances).toHaveLength(1));
    const first = MockEventSource.instances[0];

    view.rerender(
      <QueryClientProvider client={client}>
        <ThemeProvider theme={theme}>
          <StreamProbe value={OTHER_NUMBER} />
        </ThemeProvider>
      </QueryClientProvider>,
    );

    await waitFor(() => expect(MockEventSource.instances).toHaveLength(2));
    expect(first.closed).toBe(true);
    expect(MockEventSource.instances[1].url).toBe(`/api/v1/public/tracking/${OTHER_NUMBER}/stream`);
    expect(MockEventSource.instances[1].closed).toBe(false);
  });

  it('falls back to polling after three consecutive SSE failures', async () => {
    api.get.mockResolvedValue({ data: publicView() });
    renderDetail();

    await waitFor(() => expect(MockEventSource.instances).toHaveLength(1));
    const source = MockEventSource.instances[0];
    act(() => {
      source.onerror();
      source.onerror();
      source.onerror();
    });

    expect(source.closed).toBe(true);
    expect(await screen.findByText(/Checking for updates/)).toBeInTheDocument();
  });

  it('retries the SSE connection after the configured two-minute interval', () => {
    vi.useFakeTimers();
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity } },
    });
    client.setQueryData(publicTrackingQueryKey(TRACKING_NUMBER), publicView());

    const view = render(
      <QueryClientProvider client={client}>
        <StreamProbe value={TRACKING_NUMBER} />
      </QueryClientProvider>,
    );

    try {
      expect(MockEventSource.instances).toHaveLength(1);
      act(() => {
        MockEventSource.instances[0].onerror();
        MockEventSource.instances[0].onerror();
        MockEventSource.instances[0].onerror();
      });

      act(() => {
        vi.advanceTimersByTime(119999);
      });
      expect(MockEventSource.instances).toHaveLength(1);

      act(() => {
        vi.advanceTimersByTime(1);
      });
      expect(MockEventSource.instances).toHaveLength(2);
    } finally {
      view.unmount();
      client.clear();
      vi.useRealTimers();
    }
  });

  it('does not render fees, contacts, notes, proof or courier identity', async () => {
    api.get.mockResolvedValue({
      data: publicView({
        history: [
          {
            status: 'CREATED',
            at: '2026-11-12T04:00:00Z',
            failureReasonLabel: null,
            note: 'Leave at the side gate',
            actorRole: 'CUSTOMER',
            phone: '0771234567',
          },
        ],
      }),
    });
    renderDetail();
    await screen.findByRole('heading', { name: TRACKING_NUMBER });

    expect(screen.queryByText(/LKR 500\.00/)).not.toBeInTheDocument();
    expect(screen.queryByText(/0771234567/)).not.toBeInTheDocument();
    expect(screen.queryByText(/recipient@example.com/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Leave at the side gate/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Kamal/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Nimal Perera/)).not.toBeInTheDocument();
    expect(screen.queryByText(/proof-photo/)).not.toBeInTheDocument();
    expect(screen.queryByText(/merchant-secret/)).not.toBeInTheDocument();
  });
});

describe('TST-041 tracking number form', () => {
  it('opens the detail route with a trimmed upper-case number', async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    api.get.mockImplementation(() => new Promise(() => {}));

    render(
      <QueryClientProvider client={client}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/track']}>
            <Routes>
              <Route path="/track" element={<TrackPage />} />
              <Route path="/track/:trackingNumber" element={<TrackDetailPage />} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>,
    );

    await user.type(screen.getByLabelText('Tracking number'), '  sd-7k3m9qx2ta  ');
    await user.click(screen.getByRole('button', { name: 'Track' }));
    expect(await screen.findByText('Loading tracking details...')).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith(`/public/tracking/${TRACKING_NUMBER}`);
  });
});
