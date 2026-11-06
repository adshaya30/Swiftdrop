import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';
import { tokens } from '../theme/theme';

export function EmptyState({
  title = 'No records found',
  description = 'There is currently no data available for this view.',
  actionLabel,
  onAction,
  icon: Icon = InboxOutlinedIcon,
  minHeight = 280,
}) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight,
        textAlign: 'center',
        gap: 1.5,
        p: 4,
        borderRadius: tokens.radius.card,
        border: `1px dashed ${tokens.palette.border}`,
        backgroundColor: tokens.palette.surface,
      }}
    >
      <Box sx={{ color: tokens.palette.mutedText, mb: 0.5 }}>
        <Icon sx={{ fontSize: 44, opacity: 0.7 }} />
      </Box>
      <Typography variant="h6" sx={{ fontWeight: 600, color: tokens.palette.text }}>
        {title}
      </Typography>
      {description && (
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText, maxWidth: 380 }}>
          {description}
        </Typography>
      )}
      {actionLabel && onAction && (
        <Button
          variant="contained"
          onClick={onAction}
          sx={{ mt: 1, backgroundColor: tokens.palette.primary }}
        >
          {actionLabel}
        </Button>
      )}
    </Box>
  );
}

export default EmptyState;
