import React from 'react';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import DirectionsBikeOutlinedIcon from '@mui/icons-material/DirectionsBikeOutlined';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import { tokens } from '../theme/theme';

const STATUS_ICONS = {
  CREATED: AddCircleOutlineIcon,
  PICKED_UP: Inventory2OutlinedIcon,
  IN_TRANSIT: LocalShippingOutlinedIcon,
  OUT_FOR_DELIVERY: DirectionsBikeOutlinedIcon,
  DELIVERED: CheckCircleOutlineIcon,
  FAILED: ErrorOutlineIcon,
  CANCELLED: CancelOutlinedIcon,
};

/**
 * Status chip adhering strictly to SRS Section 6.2 and Master Prompt Section 10.
 * Always renders icon + label text, never color alone.
 */
export function StatusChip({ status, size = 'small' }) {
  const normStatus = (status || 'CREATED').toUpperCase();
  const config = tokens.status[normStatus] || tokens.status.CREATED;
  const IconComponent = STATUS_ICONS[normStatus] || STATUS_ICONS.CREATED;

  return (
    <Chip
      size={size}
      icon={
        <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', color: `${config.text} !important` }}>
          <IconComponent fontSize="inherit" sx={{ fontSize: '1rem' }} />
        </Box>
      }
      label={config.label}
      sx={{
        backgroundColor: config.bg,
        color: config.text,
        fontWeight: 600,
        fontSize: '0.8125rem',
        borderRadius: tokens.radius.chip,
        border: `1px solid ${config.bg}`,
        height: size === 'small' ? '26px' : '32px',
        '& .MuiChip-label': {
          px: 1,
        },
      }}
    />
  );
}

export default StatusChip;
