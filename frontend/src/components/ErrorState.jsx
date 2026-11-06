import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { tokens } from '../theme/theme';

export function ErrorState({
  title = 'Something went wrong',
  message = 'We encountered an unexpected error while loading this content. Please try again.',
  onRetry,
  minHeight = 260,
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
        border: `1px solid ${tokens.palette.border}`,
        backgroundColor: '#FEF2F2',
      }}
    >
      <ErrorOutlineIcon sx={{ fontSize: 44, color: tokens.palette.destructive }} />
      <Typography variant="h6" sx={{ fontWeight: 600, color: tokens.palette.text }}>
        {title}
      </Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText, maxWidth: 420 }}>
        {message}
      </Typography>
      {onRetry && (
        <Button
          variant="outlined"
          onClick={onRetry}
          sx={{
            mt: 1,
            color: tokens.palette.destructive,
            borderColor: tokens.palette.destructive,
            '&:hover': {
              borderColor: tokens.palette.destructiveHover,
              backgroundColor: 'rgba(220, 38, 38, 0.04)',
            },
          }}
        >
          Try Again
        </Button>
      )}
    </Box>
  );
}

export default ErrorState;
