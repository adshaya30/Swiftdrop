import React from 'react';
import { Link } from 'react-router-dom';
import StatusChip from '../../../components/StatusChip';
import { formatDateTime, formatMoney } from '../../../utils/format';
import './customer.css';

/** Customer-owned display only; all totals and statuses come from the API. */
export default function ParcelCards({ items }) {
  return <ul className="customer-parcel-cards">{items.map((parcel) => <li key={parcel.id}>
    <article aria-label={parcel.trackingNumber || 'Parcel'}>
      <div className="customer-parcel-heading">
        <Link to={`/customer/parcels/${encodeURIComponent(parcel.id)}`}>{parcel.trackingNumber || 'View parcel'}</Link>
        {parcel.status ? <StatusChip status={parcel.status} /> : <span>Status unavailable</span>}
      </div>
      <dl className="customer-parcel-facts">
        <dt>Pickup</dt><dd>{[parcel.pickup?.city, parcel.pickup?.districtName].filter(Boolean).join(', ') || 'Unavailable'}</dd>
        <dt>Drop-off</dt><dd>{[parcel.dropoff?.city, parcel.dropoff?.districtName].filter(Boolean).join(', ') || 'Unavailable'}</dd>
        <dt>Fee</dt><dd>{parcel.fee?.total != null ? formatMoney(parcel.fee.total) : 'Unavailable'}</dd>
        <dt>Created</dt><dd>{parcel.createdAt ? <time dateTime={parcel.createdAt}>{formatDateTime(parcel.createdAt)}</time> : 'Unavailable'}</dd>
      </dl>
    </article>
  </li>)}</ul>;
}
