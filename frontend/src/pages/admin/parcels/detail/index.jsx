import React, { useCallback, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { cancelAdminParcel, failAdminParcel, getAdminParcel } from '../../../../api/adminParcels';
import ErrorState from '../../../../components/ErrorState';
import LoadingState from '../../../../components/LoadingState';
import PageHeader from '../../../../components/PageHeader';
import StatusChip from '../../../../components/StatusChip';
import { tokens } from '../../../../theme/theme';
import { formatDateTime, formatMoney } from '../../../../utils/format';
import { ProofViewer } from '../../../courier/components/ProofViewer';
import { ADMIN_CANCEL_FROM, ADMIN_FAIL_FROM } from '../constants';
import { CancelParcelDialog, FailParcelDialog } from './ParcelActionDialogs';
import {
  ParcelTimeline, PlaceholderTile, SectionBoundary, getTimeline, hasTrackingStream, useTrackingStream,
} from './optionalModules';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card };

function Field({ label, children }) {
  return (
    <Box sx={{ mb: 1.5 }}>
      <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>{label}</Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.text, whiteSpace: 'pre-line' }}>{children || '—'}</Typography>
    </Box>
  );
}

function Section({ title, children }) {
  return (
    <Card elevation={0} sx={cardSx} component="section" aria-label={title}>
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 700, mb: 2, fontSize: '1rem' }}>{title}</Typography>
        {children}
      </CardContent>
    </Card>
  );
}

const address = (a) => [a?.addressLine1, a?.addressLine2, a?.city, a?.districtName, a?.postalCode].filter(Boolean).join(', ');

/**
 * FR-PAR-011, FR-PAR-012: admin parcel detail. Composes the parcel (parcel-service), the timeline (tracking-service),
 * delivery information (delivery-service) and the proof of delivery in the SPA (SRS 7.6); each section loads and fails
 * on its own. Status events refetch everything (master 9).
 */
export default function AdminParcelDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState(null); // 'cancel' | 'fail' | null
  const [notice, setNotice] = useState(null);

  const parcelQuery = useQuery({
    queryKey: ['admin', 'parcel', id],
    queryFn: () => getAdminParcel(id),
    // Polling fallback only while the live stream component is not available.
    refetchInterval: hasTrackingStream ? false : 30000,
  });
  const parcel = parcelQuery.data;

  const timelineQuery = useQuery({
    queryKey: ['admin', 'parcel-timeline', id],
    queryFn: () => getTimeline(id),
    enabled: Boolean(getTimeline && ParcelTimeline),
  });

  const refetchAll = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'parcel', id] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'parcel-timeline', id] });
  }, [queryClient, id]);

  useTrackingStream(parcel?.trackingNumber ?? null, { onStatus: refetchAll });

  const cancelMutation = useMutation({
    mutationFn: (reason) => cancelAdminParcel(id, reason),
    onSuccess: () => { setNotice('The parcel was cancelled.'); refetchAll(); },
    onError: (err) => { if (err?.status === 409) refetchAll(); },
  });
  const failMutation = useMutation({
    mutationFn: (values) => failAdminParcel(id, values),
    onSuccess: () => { setNotice('The parcel was marked as failed.'); refetchAll(); },
    onError: (err) => { if (err?.status === 409) refetchAll(); },
  });

  if (parcelQuery.isPending) return <LoadingState message="Loading parcel..." />;
  if (parcelQuery.isError) {
    return (
      <ErrorState
        title={parcelQuery.error?.status === 404 ? 'Parcel not found' : 'Something went wrong'}
        message={parcelQuery.error?.detail || 'Failed to load the parcel.'}
        onRetry={() => parcelQuery.refetch()}
      />
    );
  }

  const canCancel = ADMIN_CANCEL_FROM.includes(parcel.status);
  const canFail = ADMIN_FAIL_FROM.includes(parcel.status);

  return (
    <Box>
      <Link component={RouterLink} to="/admin/parcels" underline="hover" sx={{ display: 'inline-block', mb: 1 }}>
        ← Back to parcels
      </Link>
      <PageHeader
        title={parcel.trackingNumber}
        subtitle={`Created ${formatDateTime(parcel.createdAt)} · Updated ${formatDateTime(parcel.updatedAt)}`}
        action={<StatusChip status={parcel.status} size="medium" />}
      />

      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}

      {(canCancel || canFail) && (
        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 3 }}>
          {canCancel && (
            <Button variant="outlined" color="error" onClick={() => setDialog('cancel')}>Cancel parcel</Button>
          )}
          {canFail && (
            <Button variant="outlined" color="error" onClick={() => setDialog('fail')}>Mark as failed</Button>
          )}
        </Box>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        <Section title="Pickup">
          <Field label="Contact">{parcel.pickup?.contactName}</Field>
          <Field label="Phone">{parcel.pickup?.contactPhone}</Field>
          <Field label="Address">{address(parcel.pickup)}</Field>
        </Section>
        <Section title="Drop-off">
          <Field label="Contact">{parcel.dropoff?.contactName}</Field>
          <Field label="Phone">{parcel.dropoff?.contactPhone}</Field>
          <Field label="Email">{parcel.dropoff?.contactEmail}</Field>
          <Field label="Address">{address(parcel.dropoff)}</Field>
        </Section>
        <Section title="Parcel">
          <Field label="Weight">{parcel.parcel?.weightKg != null ? `${parcel.parcel.weightKg} kg` : null}</Field>
          <Field label="Size">{parcel.parcel?.size}</Field>
          <Field label="Description">{parcel.parcel?.description}</Field>
          <Field label="Delivery instructions">{parcel.deliveryInstructions}</Field>
          <Field label="Zones">
            {parcel.originZone && parcel.destinationZone ? `${parcel.originZone.name} → ${parcel.destinationZone.name}` : null}
          </Field>
        </Section>
        <Section title="Fee and owner">
          <Field label="Base fee">{formatMoney(parcel.fee?.baseFee)}</Field>
          <Field label="Weight fee">{formatMoney(parcel.fee?.weightFee)}</Field>
          <Field label="Size surcharge">{formatMoney(parcel.fee?.sizeSurcharge)}</Field>
          <Field label="Discount">{formatMoney(parcel.fee?.discountAmount)}</Field>
          <Field label="Total">{formatMoney(parcel.fee?.total)}</Field>
          <Field label="Owner type">{parcel.ownerType === 'MERCHANT' ? 'Merchant' : 'Customer'}</Field>
          <Field label="Customer id">{parcel.customerId}</Field>
          <Field label="Merchant id">{parcel.merchantId}</Field>
          <Field label="Merchant reference">{parcel.merchantReference}</Field>
        </Section>
      </Box>

      <Box sx={{ display: 'grid', gap: 2, mt: 2 }}>
        <Section title="Timeline">
          <SectionBoundary title="Timeline">
            {ParcelTimeline && getTimeline ? (
              timelineQuery.isPending ? <LoadingState message="Loading timeline..." minHeight={80} />
                : timelineQuery.isError
                  ? <Alert severity="warning">{timelineQuery.error?.detail || 'The timeline could not be loaded.'}</Alert>
                  : <ParcelTimeline timeline={timelineQuery.data} events={timelineQuery.data?.events} />
            ) : (
              <PlaceholderTile title="Timeline not available yet"
                message="The parcel timeline appears here once the tracking components are available." />
            )}
          </SectionBoundary>
        </Section>

        <Section title="Delivery">
          <PlaceholderTile title="Delivery information not available yet"
            message="Courier and assignment details appear here once the dispatch module is available." />
        </Section>

        {parcel.status === 'DELIVERED' && (
          <Section title="Proof of delivery">
            <SectionBoundary title="Proof of delivery">
              <ProofViewer key={parcel.updatedAt} parcelId={parcel.id} />
            </SectionBoundary>
          </Section>
        )}
      </Box>

      <CancelParcelDialog
        open={dialog === 'cancel'}
        trackingNumber={parcel.trackingNumber}
        onSubmit={(values) => cancelMutation.mutateAsync(values.reason)}
        onClose={() => setDialog(null)}
      />
      <FailParcelDialog
        open={dialog === 'fail'}
        trackingNumber={parcel.trackingNumber}
        onSubmit={(values) => failMutation.mutateAsync(values)}
        onClose={() => setDialog(null)}
      />
    </Box>
  );
}
