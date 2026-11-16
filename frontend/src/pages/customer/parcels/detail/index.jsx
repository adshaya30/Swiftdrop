import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Stack, TextField } from '@mui/material';
import { getParcel, cancelParcel } from '../../../../api/customerParcels';
import PageHeader from '../../../../components/PageHeader';
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

  return <Box className="customer-page customer-detail">
    <Button component={Link} to="/customer/parcels">Back to My Parcels</Button>
    {notice && <Alert severity="info" role="status">{notice}</Alert>}
    {detail.status === 'loading' && <LoadingState message="Loading parcel details…" />}
    {detail.status === 'error' && (detail.error?.status === 404
      ? <div role="alert"><ErrorState title="Parcel not found" message="This parcel could not be found." onRetry={refresh} /></div>
      : <CustomerRequestError error={detail.error} onRetry={refresh} />)}
    {parcel && <>
      <PageHeader title={parcel.trackingNumber} action={<StatusChip status={parcel.status} />} />
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button onClick={refresh} disabled={pending}>Refresh parcel</Button>
        {ReceiptButton ? <ReceiptButton parcelId={parcel.id} trackingNumber={parcel.trackingNumber} /> : <p>Receipt download is currently unavailable.</p>}
      </Stack>
      <p>Created: {formatDateTime(parcel.createdAt)}</p><p>Updated: {formatDateTime(parcel.updatedAt)}</p>
      {['pickup', 'dropoff'].map((side) => <section key={side} aria-label={side === 'pickup' ? 'Pickup details' : 'Drop-off details'}>
        <h2>{side === 'pickup' ? 'Pickup' : 'Drop-off'}</h2>
        <p>{parcel[side]?.contactName}</p>
        <p>{parcel[side]?.contactPhone}</p>
        {parcel[side]?.contactEmail && <p>{parcel[side].contactEmail}</p>}
        <p>{[parcel[side]?.addressLine1, parcel[side]?.addressLine2, parcel[side]?.city, parcel[side]?.districtName, parcel[side]?.postalCode].filter(Boolean).join(', ')}</p>
        <p>District code: {parcel[side]?.districtCode || 'Unavailable'}</p>
      </section>)}
      <section aria-label="Parcel information"><h2>Parcel information</h2>
        <dl className="customer-parcel-facts">
          <dt>Weight</dt><dd>{parcel.parcel?.weightKg != null ? `${parcel.parcel.weightKg} kg` : 'Unavailable'}</dd>
          <dt>Size</dt><dd>{parcel.parcel?.size || 'Unavailable'}</dd>
          <dt>Description</dt><dd>{parcel.parcel?.description || 'Not supplied'}</dd>
          <dt>Instructions</dt><dd>{parcel.deliveryInstructions || 'Not supplied'}</dd>
          <dt>Origin zone</dt><dd>{parcel.originZone?.name || 'Unavailable'}</dd>
          <dt>Destination zone</dt><dd>{parcel.destinationZone?.name || 'Unavailable'}</dd>
        </dl>
      </section>
      <section aria-label="Delivery fees"><h2>Delivery fees</h2>
        <dl className="customer-parcel-facts">{[['baseFee', 'Base fee'], ['weightFee', 'Weight fee'], ['sizeSurcharge', 'Size surcharge'], ['discountAmount', 'Discount'], ['total', 'Total']].map(([key, label]) => <React.Fragment key={key}><dt>{label}</dt><dd>{parcel.fee?.[key] != null ? formatMoney(parcel.fee[key]) : 'Unavailable'}</dd></React.Fragment>)}</dl>
      </section>
      <section aria-label="Parcel timeline"><h2>Timeline</h2>
        {!hasCustomerStatusStream && <p>Live updates are currently unavailable. Use Refresh parcel to check for changes.</p>}
        {timeline.status === 'unavailable' && <p>Parcel timeline is currently unavailable.</p>}
        {timeline.status === 'loading' && <LoadingState message="Loading timeline…" />}
        {timeline.status === 'updating' && <p role="status">Timeline updating…</p>}
        {timeline.status === 'error' && <ErrorState title="Timeline unavailable" message="The timeline could not be loaded. Please try again." onRetry={refresh} />}
        {timeline.status === 'success' && <ParcelTimeline timeline={timeline.data} />}
      </section>
      {parcel.status === 'DELIVERED' && <section aria-label="Delivery proof">{ProofViewer
        ? <ProofViewer key={`${id}:${revision}`} parcelId={id} />
        : <p>Delivery proof is currently unavailable.</p>}</section>}
      {parcel.status === 'CREATED' && <section aria-label="Cancellation"><h2>Cancel delivery</h2>
        <TextField label="Cancellation reason (optional)" multiline fullWidth value={reason} disabled={pending}
          onChange={(event) => { setReason(event.target.value); setReasonError(''); }}
          error={Boolean(reasonError)} helperText={reasonError || 'Maximum 300 characters'} inputProps={{ maxLength: 300 }} />
        <Button color="error" disabled={pending} onClick={() => { if (reason.length > 300) setReasonError('Reason must be at most 300 characters.'); else setDialog(true); }}>Cancel parcel</Button>
      </section>}
      <ConfirmDialog open={dialog} title="Cancel this delivery?" message="Confirm that you want to cancel this parcel."
        confirmText="Confirm cancellation" cancelText="Keep parcel" isDestructive loading={pending}
        onConfirm={cancel} onCancel={() => { if (!busy.current) setDialog(false); }} />
    </>}
  </Box>;
}
