import React from 'react';
import Chip from '@mui/material/Chip';
import HourglassTopOutlinedIcon from '@mui/icons-material/HourglassTopOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import HighlightOffOutlinedIcon from '@mui/icons-material/HighlightOffOutlined';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import { tokens } from '../../../theme/theme';

/**
 * Merchant status (SRS 6.2 MerchantStatus: PENDING, APPROVED, REJECTED, SUSPENDED). The shared StatusChip only knows
 * parcel statuses, so this chip reuses the theme's status colours: orange for waiting, green for approved, red for
 * rejected and slate for suspended. Always an icon plus a text label, never colour alone (master prompt section 9).
 */
export const MERCHANT_STATUSES = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'];

export const MERCHANT_STATUS_LABELS = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
};

const CONFIG = {
  PENDING: { palette: tokens.status.OUT_FOR_DELIVERY, Icon: HourglassTopOutlinedIcon },
  APPROVED: { palette: tokens.status.DELIVERED, Icon: CheckCircleOutlineIcon },
  REJECTED: { palette: tokens.status.FAILED, Icon: HighlightOffOutlinedIcon },
  SUSPENDED: { palette: tokens.status.CANCELLED, Icon: PauseCircleOutlineIcon },
};

export function MerchantStatusChip({ status, size = 'small' }) {
  const key = String(status || '').toUpperCase();
  const { palette, Icon } = CONFIG[key] || { palette: tokens.status.CREATED, Icon: HelpOutlineIcon };
  return (
    <Chip
      size={size}
      icon={<Icon sx={{ fontSize: '1rem', color: `${palette.text} !important` }} />}
      label={MERCHANT_STATUS_LABELS[key] || key || 'Unknown'}
      sx={{
        backgroundColor: palette.bg,
        color: palette.text,
        fontWeight: 600,
        borderRadius: tokens.radius.chip,
        border: `1px solid ${palette.bg}`,
      }}
    />
  );
}

export default MerchantStatusChip;
