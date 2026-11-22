import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Alert, Button, TextField } from '@mui/material';
import { ArrowLeft, Clock3, MapPin, Package, ReceiptText, RefreshCw, ShieldAlert } from 'lucide-react';
import { getParcel, cancelParcel } from '../../../../api/customerParcels';
import StatusChip from '../../../../components/StatusChip';
import LoadingState from '../../../../components/LoadingState';
import ErrorState from '../../../../components/ErrorState';
import ConfirmDialog from '../../../../components/ConfirmDialog';
import { formatDateTime, formatMoney } from '../../../../utils/format';
import CustomerRequestError from '../../components/CustomerRequestError';
import { useCustomerRequest } from '../../components/useCustomerRequest';
import { ownerIntegrations, useCustomerStatusStream, hasCustomerStatusStream } from './integrations';
import { useParcelTimeline } from './useParcelTimeline';
import './detail.css';

const feeFields = [['baseFee', 'Base fee'], ['weightFee', 'Weight fee'], ['sizeSurcharge', 'Size surcharge'], ['discountAmount', 'Discount'], ['total', 'Total']];

function CardTitle({ Icon, children }) {
  return <h2 className="customer-detail-card-title">
    <span className="customer-detail-icon"><Icon size={20} aria-hidden="true" /></span>
    {children}
  </h2>;
}

function AddressCard({ address, destination = false }) {
  const title = destination ? 'Drop-off details' : 'Pickup details';
  const fullAddress = [address?.addressLine1, address?.addressLine2, address?.city, address?.districtName, address?.postalCode]
    .filter(Boolean).join(', ');

  return <section className="customer-detail-card customer-detail-address" data-destination={destination} aria-label={title}>
    <CardTitle Icon={MapPin}>{title}</CardTitle>
    <div className="customer-detail-address-body">
      <p className="customer-detail-contact">{address?.contactName || 'Contact name unavailable'}</p>
      {address?.contactPhone && <p>{address.contactPhone}</p>}
      {address?.contactEmail && <p>{address.contactEmail}</p>}
      <p className="customer-detail-street">{fullAddress || 'Address unavailable'}</p>
      <p className="customer-detail-muted">District code: {address?.districtCode || 'Unavailable'}</p>
    </div>
  </section>;
}

export default function CustomerParcelDetailPage() {
  const { id } = useParams();
  // A keyed boundary prevents a dialog or pending mutation carrying into another parcel.
  return <ParcelDetail key={id} id={id} />;
}

function ParcelDetail({ id }) {
  const queryClient = useQueryClient();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState(false);
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');
  const [pending, setPending] = useState(false);
  const [notice, setNotice] = useState('');
  const busy = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const request = useCallback(async () => {
    const data = await getParcel(id);
    if (!data || data.id !== id || data.ownerType !== 'CUSTOMER') throw Object.assign(new Error('Parcel not found'), { status: 404 });
    return data;
  }, [id]);
  const detail = useCustomerRequest(request, id, revision);
  const refresh = useCallback(() => {
    setRevision((value) => value + 1);
    // Lists/dashboard currently load on mount. Also invalidate any owner queries
    // already registered with the shared query client; never touch other roles.
    void queryClient.invalidateQueries({ queryKey: ['customer'] });
  }, [queryClient]);
  const parcel = detail.status === 'success' ? detail.data : null;
  useCustomerStatusStream(parcel?.trackingNumber ?? null, { onStatus: refresh });
  const { ProofViewer, ReceiptButton, ParcelTimeline, getTimeline } = ownerIntegrations;
  const timeline = useParcelTimeline(id, parcel && ParcelTimeline ? getTimeline : null, revision);
  const cancel = async () => {
    if (busy.current) return;
    if (reason.length > 300) { setReasonError('Reason must be at most 300 characters.'); return; }
    busy.current = true;
    setPending(true);
    setNotice('');
    try {
      await cancelParcel(id, reason.trim() ? { reason } : {});
      if (!mounted.current) return;
      setNotice('Your parcel was cancelled.');
      setDialog(false);
      refresh();
    } catch (error) {
      if (!mounted.current) return;
      setDialog(false);
      const reasonProblem = error.errors?.find((item) => item.field === 'reason');
      if (error.status === 400 && reasonProblem) {
        setReasonError(reasonProblem.message || 'Please check the cancellation reason.');
        setNotice('Please check the cancellation reason before trying again.');
      } else if (error.status === 409 && error.code === 'INVALID_STATUS_TRANSITION') {
        setNotice('The parcel status changed and cancellation was not accepted. Refreshing the current status.');
      } else if (error.status === 404) {
        setNotice('The parcel could not be found.');
      } else if (error.status === 401 || error.status === 403) {
        setNotice('Cancellation was not authorized. Please check your session.');
      } else {
        setNotice('Cancellation could not be confirmed. Review the refreshed status before trying again.');
      }
      // Always re-read authoritative state, including lost mutation responses.
      refresh();
    } finally {
      busy.current = false;
      if (mounted.current) setPending(false);
    }
  };

  return <div className="customer-page customer-detail">
    <Link className="customer-detail-back" to="/deliveries"><ArrowLeft size={17} aria-hidden="true" />Back to My Parcels</Link>
    {notice && <Alert severity="info" role="status">{notice}</Alert>}
    {detail.status === 'loading' && <div className="customer-detail-card" role="status"><LoadingState message="Loading parcel details…" minHeight={145} /></div>}
    {detail.status === 'error' && (detail.error?.status === 404
      ? <div role="alert"><ErrorState title="Parcel not found" message="This parcel could not be found." onRetry={refresh} /></div>
      : <CustomerRequestError error={detail.error} onRetry={refresh} />)}
    {parcel && <>
      <header className="customer-detail-card customer-detail-overview">
        <div>
          <div className="customer-detail-heading"><h1>{parcel.trackingNumber}</h1><StatusChip status={parcel.status} /></div>
          <div className="customer-detail-dates">
            <p>Created: {formatDateTime(parcel.createdAt)}</p>
            <p>Updated: {formatDateTime(parcel.updatedAt)}</p>
          </div>
        </div>
        <div className="customer-detail-actions">
          <Button variant="outlined" onClick={refresh} disabled={pending} startIcon={<RefreshCw size={17} />}>Refresh parcel</Button>
          {ReceiptButton ? <ReceiptButton parcelId={parcel.id} trackingNumber={parcel.trackingNumber} /> : <p>Receipt download is currently unavailable.</p>}
        </div>
      </header>
      <div className="customer-detail-grid">
        <AddressCard address={parcel.pickup} />
        <AddressCard address={parcel.dropoff} destination />
      </div>
      <div className="customer-detail-grid">
        <section className="customer-detail-card" aria-label="Parcel information"><CardTitle Icon={Package}>Parcel information</CardTitle>
          <dl className="customer-detail-facts">
            <div><dt>Weight</dt><dd>{parcel.parcel?.weightKg != null ? `${parcel.parcel.weightKg} kg` : 'Unavailable'}</dd></div>
            <div><dt>Size</dt><dd>{parcel.parcel?.size || 'Unavailable'}</dd></div>
            <div><dt>Description</dt><dd>{parcel.parcel?.description || 'Not supplied'}</dd></div>
            <div><dt>Delivery instructions</dt><dd>{parcel.deliveryInstructions || 'Not supplied'}</dd></div>
            <div><dt>Origin zone</dt><dd>{parcel.originZone?.name || 'Unavailable'}</dd></div>
            <div><dt>Destination zone</dt><dd>{parcel.destinationZone?.name || 'Unavailable'}</dd></div>
          </dl>
        </section>
        <section className="customer-detail-card" aria-label="Delivery fees"><CardTitle Icon={ReceiptText}>Delivery fees</CardTitle>
          <dl className="customer-detail-fees">{feeFields.map(([key, label]) => <div key={key} className={key === 'total' ? 'customer-detail-total' : undefined}>
            <dt>{label}</dt><dd>{parcel.fee?.[key] != null ? formatMoney(parcel.fee[key]) : 'Unavailable'}</dd>
          </div>)}</dl>
        </section>
      </div>
      <section className="customer-detail-card" aria-label="Parcel timeline"><CardTitle Icon={Clock3}>Delivery timeline</CardTitle>
        {(!hasCustomerStatusStream || timeline.status === 'unavailable') && <div className="customer-detail-timeline-note">
          {timeline.status === 'unavailable' && <p><strong>Parcel timeline is currently unavailable.</strong></p>}
          {!hasCustomerStatusStream && <p>Live updates are currently unavailable. Use Refresh parcel to check for changes.</p>}
        </div>}
        {timeline.status === 'loading' && <LoadingState message="Loading timeline…" minHeight={112} />}
        {timeline.status === 'updating' && <p role="status">Timeline updating…</p>}
        {timeline.status === 'error' && <ErrorState title="Timeline unavailable" message="The timeline could not be loaded. Please try again." onRetry={refresh} minHeight={146} />}
        {timeline.status === 'success' && <div className="customer-detail-timeline-events"><ParcelTimeline timeline={timeline.data} /></div>}
      </section>
      {parcel.status === 'DELIVERED' && <section className="customer-detail-card" aria-label="Delivery proof">{ProofViewer
        ? <ProofViewer key={`${id}:${revision}`} parcelId={id} />
        : <p>Delivery proof is currently unavailable.</p>}</section>}
      {parcel.status === 'CREATED' && <section className="customer-detail-card customer-detail-cancellation" aria-label="Cancellation">
        <CardTitle Icon={ShieldAlert}>Cancel delivery</CardTitle>
        <label htmlFor="parcel-cancellation-reason">Cancellation reason (optional)</label>
        <TextField id="parcel-cancellation-reason" multiline minRows={2} fullWidth value={reason} disabled={pending}
          onChange={(event) => { setReason(event.target.value); setReasonError(''); }}
          error={Boolean(reasonError)} helperText={reasonError || 'Maximum 300 characters'} inputProps={{ maxLength: 300 }} />
        <div className="customer-detail-cancel-actions"><Button variant="outlined" color="error" disabled={pending} onClick={() => { if (reason.length > 300) setReasonError('Reason must be at most 300 characters.'); else setDialog(true); }}>Cancel parcel</Button></div>
      </section>}
      <ConfirmDialog open={dialog} title="Cancel this delivery?" message="Confirm that you want to cancel this parcel."
        confirmText="Confirm cancellation" cancelText="Keep parcel" isDestructive loading={pending}
        onConfirm={cancel} onCancel={() => { if (!busy.current) setDialog(false); }} />
    </>}
  </div>;
}
