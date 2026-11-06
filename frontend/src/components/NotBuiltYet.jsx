import React from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ConstructionOutlinedIcon from '@mui/icons-material/ConstructionOutlined';
import { tokens } from '../theme/theme';

export function NotBuiltYet({ routeTitle = 'Page' }) {
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '50vh',
        textAlign: 'center',
        gap: 2,
        p: 4,
      }}
    >
      <Box sx={{ p: 2, borderRadius: '50%', backgroundColor: tokens.palette.mutedSurface }}>
        <ConstructionOutlinedIcon sx={{ fontSize: 48, color: tokens.palette.primary }} />
      </Box>
      <Typography variant="h5" sx={{ fontWeight: 700, color: tokens.palette.text }}>
        {routeTitle} — Not built yet
      </Typography>
      <Typography variant="body1" sx={{ color: tokens.palette.mutedText, maxWidth: 440 }}>
        This page module is assigned to another team member and will be dynamically resolved once merged.
      </Typography>
      <Button
        variant="outlined"
        onClick={() => window.history.back()}
        sx={{ mt: 1, borderColor: tokens.palette.border, color: tokens.palette.text }}
      >
        Go Back
      </Button>
    </Box>
  );
}

export default NotBuiltYet;
