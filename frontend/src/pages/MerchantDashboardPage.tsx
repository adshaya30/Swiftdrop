import { useState } from 'react';
import { Package, Truck, CheckCircle2, XCircle, Plus, Eye } from 'lucide-react';
import StatusBadge from '../components/StatusBadge';
import './DashboardPages.css';

interface MerchantOrder {
  id: string;
  parcel: string;
  status: 'IN_TRANSIT' | 'PICKED_UP' | 'CREATED' | 'DELIVERED' | 'FAILED';
  eta: string;
  recipient: string;
}

export default function MerchantDashboardPage() {
  const [orders] = useState<MerchantOrder[]>([
    {
      id: 'ORD-1024',
      parcel: '2 kg Package',
      status: 'IN_TRANSIT',
      eta: 'Apr 28, 4 PM',
      recipient: 'Nimal Silva, Kandy',
    },
    {
      id: 'ORD-1025',
      parcel: 'Books (Batch A)',
      status: 'PICKED_UP',
      eta: 'Apr 28, 6 PM',
      recipient: 'Saman Jayasinghe, Colombo 03',
    },
    {
      id: 'ORD-1026',
      parcel: 'Electronics (Mobile)',
      status: 'CREATED',
      eta: 'Apr 29, 9 AM',
      recipient: 'Fathima R., Galle',
    },
    {
      id: 'ORD-1027',
      parcel: 'Apparel Box',
      status: 'DELIVERED',
      eta: 'Completed',
      recipient: 'Priyanka D., Kurunegala',
    },
  ]);

  const [activeModalOrder, setActiveModalOrder] = useState<MerchantOrder | null>(null);

  return (
    <div className="sd-dashboard-page">
      {/* ------------------------------------------------------------------ */}
      {/* KPI METRIC CARDS (Matches Merchant Dashboard in Reference Image)   */}
      {/* ------------------------------------------------------------------ */}
      <div className="sd-kpi-grid">
        {/* Total Orders */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Total Orders</span>
            <div className="sd-kpi-icon-box orange">
              <Package size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">248</span>
          </div>
          <span className="sd-kpi-subtext">32 orders today</span>
        </div>

        {/* In Transit */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">In Transit</span>
            <div className="sd-kpi-icon-box blue">
              <Truck size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">112</span>
          </div>
          <span className="sd-kpi-subtext">Active on routes</span>
        </div>

        {/* Delivered */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Delivered</span>
            <div className="sd-kpi-icon-box green">
              <CheckCircle2 size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">123</span>
          </div>
          <span className="sd-kpi-subtext">This billing cycle</span>
        </div>

        {/* Failed */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Failed</span>
            <div className="sd-kpi-icon-box red">
              <XCircle size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">13</span>
          </div>
          <span className="sd-kpi-subtext">Address clarification needed</span>
        </div>
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* RECENT ORDERS TABLE (Matches Reference Image)                      */}
      {/* ------------------------------------------------------------------ */}
      <div className="sd-card sd-table-card">
        <div className="sd-table-header-row">
          <div>
            <h3 className="sd-chart-title">Recent Orders</h3>
            <p className="sd-chart-subtitle">Real-time status of merchant store dispatches</p>
          </div>
          <div className="sd-table-actions">
            <button type="button" className="sd-btn sd-btn-primary sd-btn-sm">
              <Plus size={16} /> Create Order
            </button>
            <span className="sd-view-all-link">View All &rarr;</span>
          </div>
        </div>

        <div className="sd-table-responsive">
          <table className="sd-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Parcel</th>
                <th>Status</th>
                <th>ETA</th>
                <th>Recipient</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {orders.map(order => (
                <tr key={order.id} className="sd-table-row">
                  <td className="sd-cell-id">{order.id}</td>
                  <td className="sd-cell-parcel">{order.parcel}</td>
                  <td>
                    <StatusBadge status={order.status} size="sm" />
                  </td>
                  <td className="sd-cell-eta">{order.eta}</td>
                  <td className="sd-cell-recipient">{order.recipient}</td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      type="button"
                      className="sd-btn sd-btn-secondary sd-btn-xs"
                      onClick={() => setActiveModalOrder(order)}
                    >
                      <Eye size={13} /> View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Order Details Preview Modal */}
      {activeModalOrder && (
        <div className="sd-modal-backdrop" onClick={() => setActiveModalOrder(null)}>
          <div className="sd-modal-card" onClick={e => e.stopPropagation()}>
            <div className="sd-modal-header">
              <h3 className="sd-modal-title">Order Details: {activeModalOrder.id}</h3>
              <button
                type="button"
                className="sd-modal-close"
                onClick={() => setActiveModalOrder(null)}
              >
                ✕
              </button>
            </div>
            <div className="sd-modal-body">
              <div className="sd-modal-detail-row">
                <span className="sd-detail-lbl">Parcel Description:</span>
                <span className="sd-detail-val">{activeModalOrder.parcel}</span>
              </div>
              <div className="sd-modal-detail-row">
                <span className="sd-detail-lbl">Status:</span>
                <StatusBadge status={activeModalOrder.status} />
              </div>
              <div className="sd-modal-detail-row">
                <span className="sd-detail-lbl">Estimated Arrival:</span>
                <span className="sd-detail-val">{activeModalOrder.eta}</span>
              </div>
              <div className="sd-modal-detail-row">
                <span className="sd-detail-lbl">Recipient &amp; Destination:</span>
                <span className="sd-detail-val">{activeModalOrder.recipient}</span>
              </div>
            </div>
            <div className="sd-modal-footer">
              <button
                type="button"
                className="sd-btn sd-btn-primary"
                onClick={() => setActiveModalOrder(null)}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
