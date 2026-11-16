import React from 'react';
import { Link, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import LoadingState from '../../../components/LoadingState';
import ErrorState from '../../../components/ErrorState';
import StatusChip from '../../../components/StatusChip';
import TrackingEventCard from '../../../components/TrackingEventCard';
import { tokens } from '../../../theme/theme';
import { formatDateTime } from '../../../utils/format';
import { classifyTrackingError } from '../../../services/trackingService';
import { useTrackingStream } from '../hooks/useTrackingStream';

function LocationBlock({ title, location }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="overline" sx={{ color: tokens.palette.mutedText }}>
        {title}
      </Typography>
      <Typography variant="body1" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
        {location?.districtName || '—'}
      </Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText, overflowWrap: 'anywhere' }}>
        {location?.zoneName || '—'}
      </Typography>
    </Box>
  );
}

/**
 * Public parcel tracking (FR-UI-006, FR-TRK-002, FR-TRK-003).
 * Shows only the public fields and follows the SSE stream for this number.
 */
export default function TrackDetailPage() {
  const { trackingNumber = '' } = useParams();
  const { data, isLoading, error, connectionMode, refetch } = useTrackingStream(trackingNumber);

  if (isLoading) {
    return <LoadingState message="Loading tracking details..." />;
  }

  if (error) {
    const classified = classifyTrackingError(error);
    if (classified.kind === 'notFound') {
      return (
        <Box sx={{ px: 2, py: 4, maxWidth: 640, mx: 'auto', width: '100%', minWidth: 0 }}>
          <Card sx={{ p: { xs: 2.5, sm: 4 }, border: `1px solid ${tokens.palette.border}`, boxShadow: 'none' }}>
            <Typography variant="h6" component="h1" sx={{ fontWeight: 700, mb: 1 }}>
              Parcel not found
            </Typography>
            <Typography role="alert">{classified.detail}</Typography>
            <Button component={Link} to="/track" variant="contained" sx={{ mt: 3 }}>
              Try another number
            </Button>
          </Card>
        </Box>
      );
    }

    return (
      <Box sx={{ px: 2, py: 4, maxWidth: 640, mx: 'auto', width: '100%', minWidth: 0 }}>
        <ErrorState
          title={classified.kind === 'network' ? 'Network error' : 'Server error'}
          message={classified.detail}
          onRetry={() => {
            void refetch();
          }}
        />
      </Box>
    );
  }

  if (!data) {
    return <LoadingState message="Loading tracking details..." />;
  }

  const latestFailure = [...data.history].reverse().find((entry) => entry.failureReasonLabel)?.failureReasonLabel;
  const statusLabel = tokens.status[data.status]?.label || data.status || 'Unknown';
  const liveText = latestFailure
    ? `Status: ${statusLabel}. Reason: ${latestFailure}.`
    : `Status: ${statusLabel}.`;

  return (
    <Box sx={{ px: { xs: 2, sm: 3 }, py: { xs: 3, sm: 5 }, width: '100%', minWidth: 0 }}>
      <Card
        sx={{
          width: '100%',
          maxWidth: 760,
          mx: 'auto',
          p: { xs: 2.5, sm: 4 },
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          boxShadow: 'none',
        }}
      >
        <Typography variant="overline" sx={{ color: tokens.palette.mutedText }}>
          Tracking number
        </Typography>
        <Typography
          variant="h5"
          component="h1"
          sx={{ fontWeight: 700, mb: 2, overflowWrap: 'anywhere' }}
        >
          {data.trackingNumber}
        </Typography>

        <Box aria-live="polite" role="status" sx={{ mb: 2 }}>
          <StatusChip status={data.status} size="medium" />
          <Typography variant="body2" sx={{ mt: 1, color: tokens.palette.mutedText }}>
            {liveText}
            {connectionMode === 'polling' ? ' Checking for updates.' : ''}
          </Typography>
        </Box>

        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
            gap: 2,
            mb: 3,
          }}
        >
          <LocationBlock title="From" location={data.origin} />
          <LocationBlock title="To" location={data.destination} />
        </Box>

        <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 2 }}>
          Last updated {formatDateTime(data.updatedAt)}
        </Typography>

        <Typography variant="h6" component="h2" sx={{ fontWeight: 700 }}>
          Timeline
        </Typography>
        <Box component="ol" sx={{ m: 0, p: 0 }}>
          {data.history.map((entry) => (
            <TrackingEventCard
              key={`${entry.status}-${entry.at}`}
              status={entry.status}
              at={entry.at}
              failureReasonLabel={entry.failureReasonLabel}
            />
          ))}
        </Box>
      </Card>
    </Box>
  );
}
