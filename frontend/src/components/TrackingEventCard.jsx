import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import StatusChip from './StatusChip';
import { formatDateTime } from '../utils/format';
import { tokens } from '../theme/theme';

/**
 * One public timeline step. Renders status, local time and an optional
 * failure-reason label. Contact details, notes, fees and proof are not accepted.
 */
export function TrackingEventCard({ status, at, failureReasonLabel }) {
  return (
    <Box
      component="li"
      sx={{
        listStyle: 'none',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-start',
        gap: 0.75,
        py: 1.5,
        minWidth: 0,
        borderTop: `1px solid ${tokens.palette.border}`,
      }}
    >
      <StatusChip status={status} />
      <Typography variant="body2" sx={{ color: tokens.palette.text }}>
        {formatDateTime(at)}
      </Typography>
      {failureReasonLabel ? (
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText, overflowWrap: 'anywhere' }}>
          {failureReasonLabel}
        </Typography>
      ) : null}
    </Box>
  );
}

export default TrackingEventCard;
