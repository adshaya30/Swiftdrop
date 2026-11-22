import React, { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, CheckCircle2, Info, MapPin, Package, Plus } from 'lucide-react';
import LoadingState from '../../../components/LoadingState';
import EmptyState from '../../../components/EmptyState';
import { getDashboardSummary, listParcels } from '../../../api/customerParcels';
import StatusChip from '../../../components/StatusChip';
import { formatDateTime, formatMoney } from '../../../utils/format';
import CustomerRequestError from '../components/CustomerRequestError';
import { useCustomerRequest } from '../components/useCustomerRequest';
import '../components/customer.css';
import './dashboard.css';

const hasCount = (value) => Number.isSafeInteger(value) && value >= 0;

function ParcelIllustration({ delivered }) {
  return <svg className="customer-dashboard-kpi-art" viewBox="0 0 144 100" aria-hidden="true" focusable="false">
    <ellipse cx="71" cy="87" rx="48" ry="7" fill="#172033" opacity=".08" />
    <g stroke="#9a542b" strokeWidth="1.4" strokeLinejoin="round">
      <path d="M22 31 63 15l53 17-43 19z" fill="#ffd5a7" />
      <path d="m22 31 51 20v38L22 69z" fill="#f3b779" />
      <path d="m73 51 43-19v38L73 89z" fill="#df9759" />
    </g>
    <path d="m43 23 13-5 52 18-13 6z" fill="#fff0d7" />
    <path d="m95 42 13-6v17l-13 6z" fill="#ffd5a7" />
    <path d="m32 48 27 11v15L32 63z" fill="#fff8ee" />
    <path d="m37 55 16 6m-16-1 10 4" fill="none" stroke="#9a542b" strokeWidth="1.5" strokeLinecap="round" />
    {delivered && <g>
      <circle cx="115" cy="70" r="19" fill="var(--kpi-icon-color)" stroke="var(--kpi-background)" strokeWidth="3" />
      <path d="m107 70 5 5 10-11" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    </g>}
  </svg>;
}

function ParcelCount({ label, value, Icon, delivered = false }) {
  const available = hasCount(value);

  return <div className="customer-dashboard-kpi">
    <dt>
      <span className="customer-dashboard-kpi-icon"><Icon size={20} aria-hidden="true" /></span>
      {label}
      <ParcelIllustration delivered={delivered} />
    </dt>
    <dd data-available={available}>{available ? value.toLocaleString('en-LK') : 'Unavailable'}</dd>
  </div>;
}

export function DashboardCounts({ active, delivered }) {
  return <dl className="customer-dashboard-kpis" aria-label="Parcel counts">
    <ParcelCount label="Active parcels" value={active} Icon={Package} />
    <ParcelCount label="Delivered parcels" value={delivered} Icon={CheckCircle2} delivered />
  </dl>;
}

function formatLocation(location) {
  return [...new Set([location?.city, location?.districtName].filter(Boolean))].join(', ') || 'Unavailable';
}

function RecentParcels({ items }) {
  return <ul className="customer-dashboard-parcels" role="list">
    {items.map((parcel) => {
      const path = `/deliveries/${encodeURIComponent(parcel.id)}`;
      return <li key={parcel.id}>
        <article className="customer-dashboard-parcel" aria-label={parcel.trackingNumber || 'Parcel'}>
          <div className="customer-dashboard-parcel-id">
            <span className="customer-dashboard-parcel-icon"><Package size={21} aria-hidden="true" /></span>
            <div>
              <Link to={path} className="customer-dashboard-tracking">{parcel.trackingNumber || 'View parcel'}</Link>
              <div className="customer-dashboard-created">
                {parcel.createdAt ? <time dateTime={parcel.createdAt}>{formatDateTime(parcel.createdAt)}</time> : 'Creation date unavailable'}
              </div>
            </div>
          </div>
          <dl className="customer-dashboard-parcel-facts">
            <div><dt>Pickup</dt><dd>{formatLocation(parcel.pickup)}</dd></div>
            <div><dt>Drop-off</dt><dd>{formatLocation(parcel.dropoff)}</dd></div>
            <div><dt>Fee</dt><dd>{parcel.fee?.total != null ? formatMoney(parcel.fee.total) : 'Unavailable'}</dd></div>
          </dl>
          <div className="customer-dashboard-parcel-actions">
            {parcel.status ? <StatusChip status={parcel.status} /> : <span>Status unavailable</span>}
            <Link to={path} className="customer-dashboard-text-link" aria-label={parcel.trackingNumber ? `View details for ${parcel.trackingNumber}` : 'View details'}>
              View details <ArrowRight size={16} aria-hidden="true" />
            </Link>
          </div>
        </article>
      </li>;
    })}
  </ul>;
}

async function loadDashboard() {
  let summary;
  try {
    summary = await getDashboardSummary();
  } catch (error) {
    if (error?.status === 401 || error?.status === 403) throw error;
  }

  const counts = { active: summary?.activeCount, delivered: summary?.deliveredCount };
  if (Array.isArray(summary?.recentParcels)) return { ...counts, items: summary.recentParcels.slice(0, 5) };

  const recent = await listParcels({ page: 0, size: 5, sort: 'createdAt,desc' });
  if (!Array.isArray(recent?.items)) throw new Error('Invalid recent parcels');
  return { ...counts, items: recent.items.slice(0, 5) };
}

export default function CustomerDashboardPage() {
  const [revision, setRevision] = useState(0);
  const retry = useCallback(() => setRevision((value) => value + 1), []);
  const result = useCustomerRequest(loadDashboard, 'customer-dashboard', revision);
  const data = result.status === 'success' ? result.data : null;

  return <div className="customer-page customer-dashboard">
    <header className="customer-dashboard-overview">
      <div>
        <h1>Customer dashboard</h1>
        <p>Manage your deliveries, track parcels, and stay updated with SwiftDrop.</p>
      </div>
      <div className="customer-dashboard-actions">
        <Link to="/deliveries/new" className="customer-dashboard-action customer-dashboard-primary"><Plus size={19} aria-hidden="true" />New Delivery</Link>
        <Link to="/track" className="customer-dashboard-action"><MapPin size={18} aria-hidden="true" />Track Parcel</Link>
        <Link to="/deliveries" className="customer-dashboard-text-link">My Parcels <ArrowRight size={16} aria-hidden="true" /></Link>
      </div>
    </header>

    {data && <>
      <DashboardCounts active={data.active} delivered={data.delivered} />
      {(!hasCount(data.active) || !hasCount(data.delivered)) && <div className="customer-dashboard-info">
        <Info size={18} aria-hidden="true" />
        <span>Some parcel counts are currently unavailable.</span>
      </div>}
    </>}

    <section aria-labelledby="customer-recent-title">
      <div className="customer-dashboard-section-heading">
        <h2 id="customer-recent-title">Recent parcels</h2>
        {data && <span className="customer-dashboard-recent-count">{data.items.length} shown</span>}
      </div>
      {result.status === 'loading' && <div className="customer-dashboard-loading" role="status"><LoadingState message="Loading your dashboard…" minHeight={144} /></div>}
      {result.status === 'error' && <CustomerRequestError error={result.error} onRetry={retry} />}
      {data && (data.items.length
        ? <RecentParcels items={data.items} />
        : <EmptyState title="No deliveries yet" description="Create your first delivery using New Delivery above." minHeight={160} />)}
    </section>
  </div>;
}
