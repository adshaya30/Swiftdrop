import React from 'react';
import { Link } from 'react-router-dom';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { Package } from 'lucide-react';
import StatusChip from '../../../components/StatusChip';
import { formatDateTime, formatMoney } from '../../../utils/format';
import './customer.css';

function formatLocation(location) {
  return [...new Set([location?.city, location?.districtName].filter(Boolean))].join(', ') || 'Unavailable';
}

/** Customer-owned display only; all totals and statuses come from the API. */
export default function ParcelCards({ items, detailsBase = '/customer/parcels', showDetails = false }) {
  return <ul className="customer-parcel-cards" role="list">{items.map((parcel) => {
    const detailsPath = `${detailsBase}/${encodeURIComponent(parcel.id)}`;
    return <li key={parcel.id}>
      <article aria-label={parcel.trackingNumber || 'Parcel'}>
        <div className="customer-parcel-heading">
          <span className="customer-parcel-icon"><Package size={23} strokeWidth={1.8} aria-hidden="true" /></span>
          <div>
            <Link to={detailsPath}>{parcel.trackingNumber || 'View parcel'}</Link>
            <div className="customer-parcel-created">{parcel.createdAt ? <time dateTime={parcel.createdAt}>{formatDateTime(parcel.createdAt)}</time> : 'Creation date unavailable'}</div>
          </div>
        </div>
        <dl className="customer-parcel-facts">
          <div><dt>Pickup</dt><dd>{formatLocation(parcel.pickup)}</dd></div>
          <div><dt>Drop-off</dt><dd>{formatLocation(parcel.dropoff)}</dd></div>
          <div><dt>Fee</dt><dd>{parcel.fee?.total != null ? formatMoney(parcel.fee.total) : 'Unavailable'}</dd></div>
        </dl>
        <div className="customer-parcel-card-footer">
          {parcel.status ? <StatusChip status={parcel.status} /> : <span>Status unavailable</span>}
          {showDetails && <Link className="customer-parcel-details-link" to={detailsPath} aria-label={parcel.trackingNumber ? `View details for ${parcel.trackingNumber}` : 'View details'}>
            View details <ArrowForwardIcon fontSize="inherit" aria-hidden="true" />
          </Link>}
        </div>
      </article>
    </li>;
  })}</ul>;
}
