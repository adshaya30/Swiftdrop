import { useState } from 'react';
import { Package, Plus } from 'lucide-react';
import TrackingViewer from '../components/TrackingViewer';
import StatusBadge from '../components/StatusBadge';
import './DashboardPages.css';

interface CustomerParcel {
  id: string;
  recipient: string;
  destination: string;
  status: 'IN_TRANSIT' | 'PICKED_UP' | 'CREATED' | 'DELIVERED';
  date: string;
}

export default function CustomerDashboardPage() {
  const [parcels] = useState<CustomerParcel[]>([
    {
      id: 'SD-2026-00125',
      recipient: 'Kandy Electronics Hub',
      destination: 'Peradeniya Road, Kandy',
      status: 'IN_TRANSIT',
      date: 'Today, 14:30',
    },
    {
      id: 'SD-2026-00098',
      recipient: 'Saman Stores',
      destination: 'Galle Fort, Galle',
      status: 'DELIVERED',
      date: 'Apr 24, 2026',
    },
  ]);

  const [selectedTrackingCode, setSelectedTrackingCode] = useState('SD-2026-00125');

  return (
    <div className="sd-dashboard-page">
      <div className="sd-customer-welcome-card sd-card">
        <div>
          <h2 className="sd-chart-title">Customer Parcel Hub</h2>
          <p className="sd-chart-subtitle">
            Track active packages, view delivery history, and schedule courier pickups.
          </p>
        </div>
        <button type="button" className="sd-btn sd-btn-primary">
          <Plus size={16} /> Send a Package
        </button>
      </div>

      {/* Live Tracking Component */}
      <div className="sd-customer-tracking-section">
        <h3 className="sd-section-subheading">Live Parcel Progress</h3>
        <TrackingViewer initialCode={selectedTrackingCode} key={selectedTrackingCode} />
      </div>

      {/* My Parcels Quick Table */}
      <div className="sd-card sd-table-card" style={{ marginTop: '2rem' }}>
        <div className="sd-table-header-row">
          <div>
            <h3 className="sd-chart-title">My Recent Shipments</h3>
            <p className="sd-chart-subtitle">Click a parcel to view live waypoint tracking above</p>
          </div>
        </div>

        <div className="sd-table-responsive">
          <table className="sd-table">
            <thead>
              <tr>
                <th>Tracking ID</th>
                <th>Recipient</th>
                <th>Destination</th>
                <th>Status</th>
                <th>Date</th>
                <th style={{ textAlign: 'right' }}>Track</th>
              </tr>
            </thead>
            <tbody>
              {parcels.map(p => (
                <tr
                  key={p.id}
                  className={`sd-table-row ${selectedTrackingCode === p.id ? 'selected-row' : ''}`}
                  onClick={() => setSelectedTrackingCode(p.id)}
                  style={{ cursor: 'pointer' }}
                >
                  <td className="sd-cell-id">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <Package size={16} color="#F97316" />
                      <strong>{p.id}</strong>
                    </div>
                  </td>
                  <td>{p.recipient}</td>
                  <td>{p.destination}</td>
                  <td><StatusBadge status={p.status} size="sm" /></td>
                  <td className="sd-cell-eta">{p.date}</td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      type="button"
                      className="sd-btn sd-btn-secondary sd-btn-xs"
                      onClick={e => {
                        e.stopPropagation();
                        setSelectedTrackingCode(p.id);
                      }}
                    >
                      View Live
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
