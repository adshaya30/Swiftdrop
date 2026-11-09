import React from 'react';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import { tokens } from '../theme/theme';

export function LoadingState({ message = 'Loading...', minHeight = 260 }) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight,
        gap: 2,
        p: 3,
      }}
    >
      <CircularProgress size={36} sx={{ color: tokens.palette.primary }} />
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
        {message}
      </Typography>
    </Box>
  );
}

export default LoadingState;
