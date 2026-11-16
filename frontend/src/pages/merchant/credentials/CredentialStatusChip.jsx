import React from 'react';
import Chip from '@mui/material/Chip';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined';
import { tokens } from '../../../theme/theme';

/**
 * Credential status (SRS 6.2 CredentialStatus: ACTIVE, REVOKED). Always an icon plus a text label, never colour alone
 * (master prompt section 9). Colours come from the shared theme tokens.
 */
const CONFIG = {
  ACTIVE: { label: 'Active', palette: tokens.status.DELIVERED, Icon: CheckCircleOutlineIcon },
  REVOKED: { label: 'Revoked', palette: tokens.status.CANCELLED, Icon: BlockOutlinedIcon },
};

export default function CredentialStatusChip({ status }) {
  const key = String(status || '').toUpperCase();
  const { label, palette, Icon } = CONFIG[key] || { label: key || 'Unknown', palette: tokens.status.CREATED, Icon: BlockOutlinedIcon };
  return (
    <Chip
      size="small"
      icon={<Icon sx={{ fontSize: '1rem', color: `${palette.text} !important` }} />}
      label={label}
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
