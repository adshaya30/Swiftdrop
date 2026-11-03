import { useState, type FormEvent } from 'react';
import { Search, Package, MapPin, Truck, CheckCircle2, Clock } from 'lucide-react';
import StatusBadge from './StatusBadge';
import './TrackingViewer.css';

export interface TrackingStep {
  label: string;
  timestamp: string;
  status: 'completed' | 'active' | 'pending';
  icon: 'created' | 'pickedup' | 'intransit' | 'outfordelivery' | 'delivered';
}

export interface ParcelTrackingInfo {
  trackingNumber: string;
  senderCity: string;
  recipientCity: string;
  recipientAddress: string;
  currentStatus: 'CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED';
  estimatedDelivery: string;
  steps: TrackingStep[];
}

const DEFAULT_DEMO_TRACKING: ParcelTrackingInfo = {
  trackingNumber: 'SD-2026-00125',
  senderCity: 'Colombo 07',
  recipientCity: 'Kandy',
  recipientAddress: '42 Peradeniya Road, Kandy',
  currentStatus: 'IN_TRANSIT',
  estimatedDelivery: 'Today by 5:30 PM',
  steps: [
    {
      label: 'Created',
      timestamp: 'Apr 27, 09:00',
      status: 'completed',
      icon: 'created',
    },
    {
      label: 'Picked Up',
      timestamp: 'Apr 27, 11:20',
      status: 'completed',
      icon: 'pickedup',
    },
    {
      label: 'In Transit',
      timestamp: 'Apr 27, 14:30',
      status: 'active',
      icon: 'intransit',
    },
    {
      label: 'Out for Delivery',
      timestamp: 'Pending',
      status: 'pending',
      icon: 'outfordelivery',
    },
    {
      label: 'Delivered',
      timestamp: 'Estimated 17:30',
      status: 'pending',
      icon: 'delivered',
    },
  ],
};

interface TrackingViewerProps {
  initialCode?: string;
  compact?: boolean;
}

export function TrackingViewer({
  initialCode = 'SD-2026-00125',
  compact = false,
}: TrackingViewerProps) {
  const [searchInput, setSearchInput] = useState(initialCode);
  const [trackingData, setTrackingData] = useState<ParcelTrackingInfo>(DEFAULT_DEMO_TRACKING);
  const [hasSearched, setHasSearched] = useState(true);

  const handleSearch = (e: FormEvent) => {
    e.preventDefault();
    if (!searchInput.trim()) return;

    // Simulate lookup response
    const code = searchInput.trim().toUpperCase();
    setTrackingData({
      ...DEFAULT_DEMO_TRACKING,
      trackingNumber: code.startsWith('SD-') ? code : `SD-${code}`,
    });
    setHasSearched(true);
  };

  return (
    <div className={`sd-tracking-container ${compact ? 'compact' : ''}`}>
      {/* Tracking Input Bar */}
      <form onSubmit={handleSearch} className="sd-tracking-form">
        <div className="sd-tracking-input-wrapper">
          <Search className="sd-tracking-icon" size={18} />
          <input
            type="text"
            className="sd-tracking-input"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Enter tracking number (e.g. SD-2026-00125)"
            aria-label="Tracking Number"
          />
        </div>
        <button type="submit" className="sd-btn sd-btn-primary sd-tracking-submit">
          Track
        </button>
      </form>

      {hasSearched && trackingData && (
        <div className="sd-tracking-result-card">
          {/* Visual Progress Stepper (Timeline) matching Reference Image */}
          <div className="sd-stepper">
            {trackingData.steps.map((step, index) => {
              const isCompleted = step.status === 'completed';
              const isActive = step.status === 'active';

              return (
                <div
                  key={step.label}
                  className={`sd-stepper-item ${step.status}`}
                  style={{ flex: 1, position: 'relative' }}
                >
                  {/* Step Connector Line */}
                  {index < trackingData.steps.length - 1 && (
                    <div
                      className="sd-stepper-line"
                      style={{
                        position: 'absolute',
                        top: '18px',
                        left: '50%',
                        width: '100%',
                        height: '3px',
                        backgroundColor: isCompleted ? '#16A34A' : isActive ? '#F97316' : '#E2E8F0',
                        zIndex: 1,
                      }}
                    />
                  )}

                  {/* Step Node */}
                  <div className="sd-stepper-node-wrapper" style={{ position: 'relative', zIndex: 2, textAlign: 'center' }}>
                    <div
                      className={`sd-stepper-node ${step.status}`}
                      style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '50%',
                        margin: '0 auto 8px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backgroundColor: isCompleted
                          ? '#16A34A'
                          : isActive
                          ? '#F97316'
                          : '#FFFFFF',
                        color: isCompleted || isActive ? '#FFFFFF' : '#94A3B8',
                        border: isCompleted || isActive ? 'none' : '2px solid #CBD5E1',
                        boxShadow: isActive ? '0 0 0 4px rgba(249, 115, 22, 0.22)' : 'none',
                        transition: 'all 0.2s ease',
                      }}
                    >
                      {isCompleted ? (
                        <CheckCircle2 size={18} />
                      ) : isActive ? (
                        <Truck size={18} />
                      ) : (
                        <span style={{ fontSize: '12px', fontWeight: 700 }}>{index + 1}</span>
                      )}
                    </div>

                    <div className="sd-stepper-label" style={{ fontWeight: 700, fontSize: '0.8125rem', color: isActive ? '#EA580C' : '#172033' }}>
                      {step.label}
                    </div>
                    <div className="sd-stepper-time" style={{ fontSize: '0.725rem', color: '#64748B', marginTop: '2px' }}>
                      {step.timestamp}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Lower Section: Parcel Summary + Route Map Preview */}
          <div className="sd-tracking-details-grid">
            {/* Summary Details */}
            <div className="sd-tracking-summary-card">
              <div className="sd-summary-header">
                <div className="sd-summary-icon-box">
                  <Package size={22} color="#F97316" />
                </div>
                <div>
                  <h4 className="sd-summary-id">{trackingData.trackingNumber}</h4>
                  <p className="sd-summary-route">
                    From: <strong>{trackingData.senderCity}</strong> &rarr; To: <strong>{trackingData.recipientCity}</strong>
                  </p>
                </div>
              </div>

              <div className="sd-summary-meta">
                <div className="sd-meta-item">
                  <span className="sd-meta-label">Current Status</span>
                  <StatusBadge status={trackingData.currentStatus} />
                </div>
                <div className="sd-meta-item">
                  <span className="sd-meta-label">Estimated Delivery</span>
                  <span className="sd-meta-value" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <Clock size={14} color="#EA580C" />
                    <strong>{trackingData.estimatedDelivery}</strong>
                  </span>
                </div>
                <div className="sd-meta-item">
                  <span className="sd-meta-label">Destination</span>
                  <span className="sd-meta-value" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                    <MapPin size={14} color="#64748B" />
                    {trackingData.recipientAddress}
                  </span>
                </div>
              </div>
            </div>

            {/* Visual Route Graphic / Map Simulation */}
            <div className="sd-tracking-map-card">
              <div className="sd-map-header">
                <span className="sd-map-title">Live Route Waypoints</span>
                <span className="sd-map-live-tag">
                  <span className="sd-pulse-dot" /> GPS Active
                </span>
              </div>
              <div className="sd-map-canvas">
                <svg viewBox="0 0 340 140" fill="none" xmlns="http://www.w3.org/2000/svg" className="sd-map-svg">
                  {/* Map Terrain Grid Lines */}
                  <path d="M10 20C80 20 120 40 180 35C240 30 280 60 330 50" stroke="#E2E8F0" strokeWidth="1.5" strokeDasharray="3 3" />
                  <path d="M20 110C90 90 140 120 220 95C270 80 300 110 330 100" stroke="#E2E8F0" strokeWidth="1.5" strokeDasharray="3 3" />

                  {/* Route Road (Curved Highway Line) */}
                  <path
                    d="M40 95 C 100 95, 140 45, 200 45 C 250 45, 270 85, 300 85"
                    stroke="#FDBA74"
                    strokeWidth="6"
                    strokeLinecap="round"
                  />
                  <path
                    d="M40 95 C 100 95, 140 45, 200 45 C 250 45, 270 85, 300 85"
                    stroke="#F97316"
                    strokeWidth="3"
                    strokeLinecap="round"
                    strokeDasharray="6 4"
                  />

                  {/* Origin Pin (Colombo) */}
                  <circle cx="40" cy="95" r="7" fill="#172033" />
                  <circle cx="40" cy="95" r="3" fill="#FFFFFF" />
                  <text x="25" y="118" fill="#172033" fontSize="10" fontWeight="700">Colombo</text>

                  {/* Active Vehicle Pin (In Transit) */}
                  <g transform="translate(195, 30)">
                    <rect x="0" y="0" width="28" height="24" rx="6" fill="#EA580C" />
                    <text x="14" y="16" fill="#FFFFFF" fontSize="11" textAnchor="middle">🚚</text>
                  </g>

                  {/* Destination Pin (Kandy) */}
                  <circle cx="300" cy="85" r="7" fill="#16A34A" />
                  <circle cx="300" cy="85" r="3" fill="#FFFFFF" />
                  <text x="285" y="108" fill="#16A34A" fontSize="10" fontWeight="700">Kandy</text>
                </svg>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default TrackingViewer;
