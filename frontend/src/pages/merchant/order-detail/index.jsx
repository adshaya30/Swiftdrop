import React, { useCallback, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import PageHeader from '../../../components/PageHeader';
import StatusChip from '../../../components/StatusChip';
import LoadingState from '../../../components/LoadingState';
import ErrorState from '../../../components/ErrorState';
import ProofViewer from '../../courier/components/ProofViewer';
import ReceiptButton from '../components/ReceiptButton';
import CancelOrderDialog, { cancelErrorMessage } from '../components/CancelOrderDialog';
import { ParcelTimeline, getTimeline, hasTrackingStream, useStatusChanges } from '../components/trackingAdapter';
import { getOrder } from '../../../api/merchantOrders';
import { formatDateTime, formatMoney } from '../../../utils/format';
import { tokens } from '../../../theme/theme';

/** FR-UI-007: right after creation the timeline may be 404 for a few seconds: retry every 2 s for up to 30 s. */
export const TIMELINE_RETRY_MS = 2000;
export const TIMELINE_RETRIES = 15;
/** Polling fallback only while the live stream is not available (SRS 3.8). */
export const FALLBACK_REFRESH_MS = 30000;

function Section({ title, children }) {
  const id = `section-${title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;
  return (
    <Card component="section" aria-labelledby={id}>
      <CardContent>
        <Typography id={id} component="h2" variant="h6" sx={{ fontSize: '1rem', mb: 1.5 }}>{title}</Typography>
        {children}
      </CardContent>
    </Card>
  );
}

function Field({ label, children }) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '160px 1fr' }, gap: { xs: 0, sm: 2 }, py: 0.5 }}>
      <Typography component="dt" variant="body2" sx={{ color: tokens.palette.mutedText }}>{label}</Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, overflowWrap: 'anywhere' }}>
        {children === null || children === undefined || children === '' ? '—' : children}
      </Typography>
    </Box>
  );
}

function Contact({ party }) {
  if (!party) return null;
  return (
    <Box component="dl" sx={{ m: 0 }}>
      <Field label="Contact">{party.contactName}</Field>
      <Field label="Phone">{party.contactPhone}</Field>
      {party.contactEmail !== undefined && <Field label="Email">{party.contactEmail}</Field>}
      <Field label="Address">
        {[party.addressLine1, party.addressLine2, party.city, party.districtName, party.postalCode]
          .filter(Boolean).join(', ')}
      </Field>
    </Box>
  );
}

function TimelineSection({ id }) {
  const timeline = useQuery({
    queryKey: ['merchant', 'order', id, 'timeline'],
    queryFn: () => getTimeline(id),
    enabled: Boolean(getTimeline && ParcelTimeline),
    retry: (count, error) => error?.status === 404 && count < TIMELINE_RETRIES,
    retryDelay: TIMELINE_RETRY_MS,
  });

  if (!getTimeline || !ParcelTimeline) {
    return (
      <Typography role="status" variant="body2" sx={{ color: tokens.palette.mutedText }}>
        The status timeline appears here once tracking is available.
      </Typography>
    );
  }
  if (timeline.isPending) {
    return <Typography role="status" variant="body2">Timeline updating…</Typography>;
  }
  if (timeline.isError) {
    return <Alert severity="warning">The timeline could not be loaded. It will appear when tracking catches up.</Alert>;
  }
  return <ParcelTimeline timeline={timeline.data} events={timeline.data?.history} />;
}

/** FR-PAR-008, SRS 7.6: merchant order detail composed from parcel, tracking and delivery services. */
export default function MerchantOrderDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [notice, setNotice] = useState(null);

  const orderQuery = useQuery({
    queryKey: ['merchant', 'order', id],
    queryFn: () => getOrder(id),
    refetchInterval: hasTrackingStream ? false : FALLBACK_REFRESH_MS,
    retry: (count, error) => error?.status !== 404 && count < 1,
  });
  const order = orderQuery.data;

  const refetchAll = useCallback(() => {
    // FR-UI-007: detail, timeline and proof (keyed by updatedAt) all refresh on a status event.
    queryClient.invalidateQueries({ queryKey: ['merchant', 'order', id] });
  }, [queryClient, id]);

  useStatusChanges(order?.trackingNumber, refetchAll);

  if (orderQuery.isPending) {
    return <LoadingState message="Loading order..." />;
  }
  if (orderQuery.isError) {
    const notFound = orderQuery.error?.status === 404;
    return (
      <Box sx={{ maxWidth: 900, mx: 'auto' }}>
        <ErrorState
          title={notFound ? 'Order not found' : 'Order unavailable'}
          message={notFound ? 'This order does not exist or belongs to another merchant.'
            : 'We could not load this order. Please try again.'}
          onRetry={notFound ? undefined : () => orderQuery.refetch()}
        />
        <Link component={RouterLink} to="/merchant/orders" sx={{ display: 'inline-block', mt: 2 }}>
          Back to orders
        </Link>
      </Box>
    );
  }

  const fee = order.fee ?? {};

  return (
    <Box sx={{ maxWidth: 1100, mx: 'auto', width: '100%' }}>
      <Link component={RouterLink} to="/merchant/orders" sx={{ display: 'inline-block', mb: 1 }}>
        ← Back to orders
      </Link>
      <PageHeader
        title={order.trackingNumber}
        subtitle={order.merchantReference ? `Reference ${order.merchantReference}` : 'No merchant reference'}
        action={(
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, alignItems: 'flex-start' }}>
            <StatusChip status={order.status} size="medium" />
            <ReceiptButton parcelId={order.id} trackingNumber={order.trackingNumber} />
            {order.status === 'CREATED' && (
              <Button variant="outlined" color="error" onClick={() => setCancelOpen(true)} sx={{ minHeight: 44 }}>
                Cancel order
              </Button>
            )}
          </Box>
        )}
      />

      {notice && (
        <Alert severity={notice.severity} onClose={() => setNotice(null)} sx={{ mb: 2 }} role="status">
          {notice.text}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' } }}>
        <Section title="Pickup"><Contact party={order.pickup} /></Section>
        <Section title="Drop-off"><Contact party={order.dropoff} /></Section>
        <Section title="Parcel">
          <Box component="dl" sx={{ m: 0 }}>
            <Field label="Weight">{order.parcel?.weightKg !== undefined ? `${order.parcel.weightKg} kg` : null}</Field>
            <Field label="Size">{order.parcel?.size}</Field>
            <Field label="Description">{order.parcel?.description}</Field>
            <Field label="Instructions">{order.deliveryInstructions}</Field>
            <Field label="Created">{formatDateTime(order.createdAt)}</Field>
            <Field label="Last update">{formatDateTime(order.updatedAt)}</Field>
          </Box>
        </Section>
        <Section title="Fee">
          <Box component="dl" sx={{ m: 0 }}>
            <Field label="Base fee">{formatMoney(fee.baseFee)}</Field>
            <Field label="Weight fee">{formatMoney(fee.weightFee)}</Field>
            <Field label="Size surcharge">{formatMoney(fee.sizeSurcharge)}</Field>
            <Field label="Discount">{formatMoney(fee.discountAmount)}</Field>
            <Field label="Total"><strong>{formatMoney(fee.total)}</strong></Field>
          </Box>
        </Section>
      </Box>

      <Box sx={{ display: 'grid', gap: 2, mt: 2 }}>
        <Section title="Status timeline">
          <Box aria-live="polite"><TimelineSection id={order.id} /></Box>
        </Section>
        {order.status === 'DELIVERED' && (
          <Section title="Proof of delivery">
            <ProofViewer key={order.updatedAt} parcelId={order.id} />
          </Section>
        )}
      </Box>

      <CancelOrderDialog
        open={cancelOpen}
        order={order}
        onClose={() => setCancelOpen(false)}
        onCancelled={() => {
          setCancelOpen(false);
          setNotice({ severity: 'success', text: 'The order was cancelled.' });
          refetchAll();
        }}
        onFailed={(error) => {
          if (error?.code === 'INVALID_STATUS_TRANSITION' || error?.code === 'CONCURRENT_UPDATE') {
            setCancelOpen(false);
            setNotice({ severity: 'warning', text: cancelErrorMessage(error) });
            refetchAll();
          }
        }}
      />
    </Box>
  );
}
