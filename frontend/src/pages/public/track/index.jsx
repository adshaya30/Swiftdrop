import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import { tokens } from '../../../theme/theme';

/**
 * Public tracking entry (FR-UI-006). The number is trimmed and upper-cased,
 * then the existing /track/:trackingNumber route shows the live view.
 */
export default function TrackPage() {
  const navigate = useNavigate();
  const [trackingNumber, setTrackingNumber] = useState('');
  const [formError, setFormError] = useState('');

  const onSubmit = (event) => {
    event.preventDefault();
    const number = trackingNumber.trim().toUpperCase();
    if (!number) {
      setFormError('Enter a tracking number.');
      return;
    }
    navigate(`/track/${encodeURIComponent(number)}`);
  };

  return (
    <Box sx={{ px: { xs: 2, sm: 3 }, py: { xs: 3, sm: 6 }, width: '100%', minWidth: 0 }}>
      <Card
        component="form"
        onSubmit={onSubmit}
        sx={{
          width: '100%',
          maxWidth: 560,
          mx: 'auto',
          p: { xs: 2.5, sm: 4 },
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          boxShadow: 'none',
        }}
      >
        <Typography variant="h5" component="h1" sx={{ fontWeight: 700, mb: 1 }}>
          Track a parcel
        </Typography>
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 3 }}>
          Enter the tracking number to see the current status and timeline.
        </Typography>
        <TextField
          id="tracking-number"
          name="trackingNumber"
          label="Tracking number"
          value={trackingNumber}
          onChange={(event) => {
            setTrackingNumber(event.target.value);
            if (formError) setFormError('');
          }}
          error={Boolean(formError)}
          helperText={formError || 'Example: SD-7K3M9QX2TA'}
          fullWidth
          autoComplete="off"
          slotProps={{ htmlInput: { 'aria-label': 'Tracking number' } }}
        />
        <Button
          type="submit"
          variant="contained"
          fullWidth
          sx={{
            mt: 2,
            height: tokens.controlHeight.standard,
            backgroundColor: tokens.palette.primary,
            fontWeight: 600,
          }}
        >
          Track
        </Button>
      </Card>
    </Box>
  );
}
