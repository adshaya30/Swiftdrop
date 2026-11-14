import React, { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { downloadReceipt, receiptFileName } from '../../../api/receipts';
import { tokens } from '../../../theme/theme';

/**
 * Downloads the delivery receipt PDF (FR-PAR-010, FR-UI-011, master 9). The PDF needs the bearer token, so it is
 * fetched through the shared API client and saved from a Blob URL that is revoked straight away: never a plain link
 * to the API and never window.open. Reused by the customer, merchant and admin detail pages.
 */
export function ReceiptButton({ parcelId, trackingNumber, variant = 'outlined', fullWidth = false }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleDownload = async () => {
    setLoading(true);
    setError(null);
    let url = null;
    try {
      const blob = await downloadReceipt(parcelId);
      url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = receiptFileName(trackingNumber);
      link.rel = 'noopener';
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      setError(err?.status === 404
        ? 'The receipt for this parcel could not be found.'
        : 'The receipt could not be downloaded. Please try again.');
    } finally {
      if (url) URL.revokeObjectURL(url);
      setLoading(false);
    }
  };

  return (
    <Box sx={{ display: 'inline-flex', flexDirection: 'column', gap: 0.5, width: fullWidth ? '100%' : 'auto' }}>
      <Button
        variant={variant}
        onClick={handleDownload}
        disabled={loading || !parcelId}
        fullWidth={fullWidth}
        startIcon={loading
          ? <CircularProgress size={16} sx={{ color: 'inherit' }} aria-hidden="true" />
          : <ReceiptLongOutlinedIcon />}
        aria-busy={loading}
        sx={{ minHeight: 44 }}
      >
        {loading ? 'Preparing receipt...' : 'Download receipt'}
      </Button>
      {error && (
        <Typography role="alert" variant="body2" sx={{ color: tokens.palette.destructive }}>
          {error}
        </Typography>
      )}
    </Box>
  );
}

export default ReceiptButton;
