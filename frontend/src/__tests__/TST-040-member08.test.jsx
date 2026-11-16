import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { theme } from '../theme/theme';
import * as courierApi from '../api/courier';
import * as proofsApi from '../api/proofs';
import CourierDeliveriesPage from '../pages/courier/deliveries/index.jsx';
import CourierDeliveryDetailPage from '../pages/courier/delivery-detail/index.jsx';
import { ProofViewer } from '../pages/courier/components/ProofViewer.jsx';

// Mock API modules
vi.mock('../api/courier');
vi.mock('../api/proofs');

function renderWithTheme(ui) {
  return render(
    <ThemeProvider theme={theme}>
      {ui}
    </ThemeProvider>
  );
}

describe('TST-040 Member #8 Courier Operations Frontend', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    global.URL.createObjectURL = vi.fn(() => 'blob:mock-proof-url');
    global.URL.revokeObjectURL = vi.fn();
  });

  describe('CourierDeliveriesPage (FR-DEL-003)', () => {
    const mockDeliveriesResponse = {
      items: [
        {
          parcelId: 'parcel-101',
          trackingNumber: 'SWIFT-101',
          status: 'OUT_FOR_DELIVERY',
          pickupCity: 'Colombo 03',
          dropoffCity: 'Dehiwala',
          originDistrictName: 'Colombo',
          destinationDistrictName: 'Colombo',
          assignedAt: '2026-11-12T04:00:00Z',
        },
        {
          parcelId: 'parcel-102',
          trackingNumber: 'SWIFT-102',
          status: 'DELIVERED',
          pickupCity: 'Colombo 07',
          dropoffCity: 'Nugegoda',
          originDistrictName: 'Colombo',
          destinationDistrictName: 'Colombo',
          assignedAt: '2026-11-12T03:00:00Z',
        },
      ],
      page: 0,
      size: 100,
      totalItems: 2,
      totalPages: 1,
    };

    it('renders deliveries list and switches scope tabs', async () => {
      courierApi.listDeliveries.mockResolvedValue(mockDeliveriesResponse);

      renderWithTheme(
        <MemoryRouter>
          <CourierDeliveriesPage />
        </MemoryRouter>
      );

      // Verify header and initial today scope call
      expect(screen.getByText('My Deliveries')).toBeInTheDocument();
      await waitFor(() => {
        expect(courierApi.listDeliveries).toHaveBeenCalledWith('today', 0, 100);
      });

      // Verify list items rendered (getAllByText since SWIFT-101 is in banner and list)
      await waitFor(() => {
        expect(screen.getAllByText('SWIFT-101').length).toBeGreaterThanOrEqual(1);
        expect(screen.getByText('SWIFT-102')).toBeInTheDocument();
        expect(screen.getAllByText(/Colombo 03 → Dehiwala/i).length).toBeGreaterThanOrEqual(1);
      });

      // Next Stop banner for OUT_FOR_DELIVERY parcel
      expect(screen.getByText(/Next Stop/i)).toBeInTheDocument();

      // Click "Active" tab
      const activeTab = screen.getByRole('button', { name: /Active/i });
      fireEvent.click(activeTab);

      await waitFor(() => {
        expect(courierApi.listDeliveries).toHaveBeenCalledWith('active', 0, 100);
      });
    });

    it('shows empty state when no deliveries match scope', async () => {
      courierApi.listDeliveries.mockResolvedValueOnce({
        items: [],
        page: 0,
        size: 100,
        totalItems: 0,
        totalPages: 0,
      });

      renderWithTheme(
        <MemoryRouter>
          <CourierDeliveriesPage />
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('No Deliveries Found')).toBeInTheDocument();
      });
    });
  });

  describe('CourierDeliveryDetailPage (FR-DEL-004, FR-DEL-005, FR-DEL-007, FR-DEL-008)', () => {
    const mockDetail = {
      parcelId: 'parcel-201',
      trackingNumber: 'SWIFT-201',
      status: 'OUT_FOR_DELIVERY',
      statusSequence: 2,
      allowedNextStatuses: ['DELIVERED', 'FAILED'],
      pickup: {
        contactName: 'Kamal Silva',
        contactPhone: '+94771112233',
        addressLine1: '12 Galle Rd',
        city: 'Colombo 03',
        districtCode: 'LK-11',
        districtName: 'Colombo',
      },
      dropoff: {
        contactName: 'Nimal Fernando',
        contactPhone: '+94774445566',
        addressLine1: '45 Station Rd',
        city: 'Dehiwala',
        districtCode: 'LK-11',
        districtName: 'Colombo',
      },
      parcel: {
        weightKg: 1.5,
        pieces: 1,
        dimensions: { lengthCm: 20, widthCm: 15, heightCm: 10 },
      },
      deliveryInstructions: 'Ring doorbell twice',
      safetyNotice: 'Fragile glassware inside',
      pickupCity: 'Colombo 03',
      dropoffCity: 'Dehiwala',
      assignedAt: '2026-11-12T04:00:00Z',
    };

    it('renders delivery details, safety notice and touch-target action buttons', async () => {
      courierApi.getDelivery.mockResolvedValueOnce(mockDetail);

      renderWithTheme(
        <MemoryRouter initialEntries={['/courier/deliveries/parcel-201']}>
          <Routes>
            <Route path="/courier/deliveries/:id" element={<CourierDeliveryDetailPage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText('SWIFT-201')).toBeInTheDocument();
        expect(screen.getByText('Nimal Fernando')).toBeInTheDocument();
        expect(screen.getByText('+94774445566')).toBeInTheDocument();
        expect(screen.getByText(/Update the status only when you have stopped safely/i)).toBeInTheDocument();
        expect(screen.getByText('Ring doorbell twice')).toBeInTheDocument();
      });

      // Action buttons: "Mark delivered" and "Report failed delivery"
      const deliveredBtn = screen.getByRole('button', { name: /Mark delivered/i });
      const failedBtn = screen.getByRole('button', { name: /Report failed delivery/i });
      expect(deliveredBtn).toBeInTheDocument();
      expect(failedBtn).toBeInTheDocument();
    });

    it('opens Mark Delivered dialog and submits proof (FR-DEL-007, FR-DEL-008)', async () => {
      courierApi.getDelivery.mockResolvedValue(mockDetail);
      courierApi.updateStatus.mockResolvedValueOnce({
        parcelId: 'parcel-201',
        status: 'DELIVERED',
        sequence: 3,
        statusChangedAt: '2026-11-12T05:00:00Z',
      });

      renderWithTheme(
        <MemoryRouter initialEntries={['/courier/deliveries/parcel-201']}>
          <Routes>
            <Route path="/courier/deliveries/:id" element={<CourierDeliveryDetailPage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Mark delivered/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Mark delivered/i }));

      // Delivered dialog should be open
      expect(screen.getByText('Confirm Delivery')).toBeInTheDocument();
      const recipientInput = screen.getByLabelText(/Recipient Name \*/i);
      expect(recipientInput).toBeInTheDocument();

      fireEvent.change(recipientInput, { target: { value: 'Nimal Fernando' } });

      const confirmBtn = screen.getByRole('button', { name: /Mark Delivered/i });
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(courierApi.updateStatus).toHaveBeenCalledWith(
          'parcel-201',
          expect.any(FormData)
        );
      });
    });

    it('opens Failed Delivery dialog with reason selector', async () => {
      courierApi.getDelivery.mockResolvedValue(mockDetail);

      renderWithTheme(
        <MemoryRouter initialEntries={['/courier/deliveries/parcel-201']}>
          <Routes>
            <Route path="/courier/deliveries/:id" element={<CourierDeliveryDetailPage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /Report failed delivery/i })).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /Report failed delivery/i }));

      // Dialog opens
      expect(screen.getByText('Report Delivery Failure')).toBeInTheDocument();
    });
  });

  describe('ProofViewer Component (FR-DEL-007, FR-UI-011)', () => {
    it('loads proof metadata and photo via apiBlob, revoking blob URL on unmount', async () => {
      const mockProof = {
        parcelId: 'parcel-301',
        recipientName: 'Sunil Dias',
        note: 'Left with security officer',
        hasPhoto: true,
        capturedAt: '2026-11-12T06:00:00Z',
      };
      const mockBlob = new Blob(['mock-image-data'], { type: 'image/jpeg' });

      proofsApi.getProof.mockResolvedValueOnce(mockProof);
      proofsApi.getProofPhoto.mockResolvedValueOnce(mockBlob);

      const { unmount } = renderWithTheme(<ProofViewer parcelId="parcel-301" />);

      await waitFor(() => {
        expect(proofsApi.getProof).toHaveBeenCalledWith('parcel-301');
        expect(proofsApi.getProofPhoto).toHaveBeenCalledWith('parcel-301');
      });

      await waitFor(() => {
        expect(screen.getByText('Sunil Dias')).toBeInTheDocument();
        expect(screen.getByText(/Left with security officer/i)).toBeInTheDocument();
        expect(global.URL.createObjectURL).toHaveBeenCalledWith(mockBlob);
      });

      // Unmount should revoke the created object URL
      unmount();
      expect(global.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-proof-url');
    });
  });
});
