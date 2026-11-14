import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { cancelOrder } from '../../../api/merchantOrders';
import { tokens } from '../../../theme/theme';

export const REASON_MAX = 300;

/** User-facing text for the errors of POST /merchant/orders/{id}/cancel (SRS 7.2, NFR-USA-001). */
export function cancelErrorMessage(error) {
  switch (error?.code) {
    case 'MERCHANT_NOT_ACTIVE':
      return 'Your merchant account is not active, so orders cannot be cancelled. Please contact SwiftDrop.';
    case 'INVALID_STATUS_TRANSITION':
      return 'This order can no longer be cancelled because it has already been picked up or closed.';
    case 'CONCURRENT_UPDATE':
      return 'This order was just updated. Please check its status and try again.';
    case 'NOT_FOUND':
      return 'This order could not be found.';
    case 'VALIDATION_ERROR':
      return `The reason can be at most ${REASON_MAX} characters.`;
    default:
      return 'The order could not be cancelled. Please try again.';
  }
}

/**
 * Destructive-action confirmation for cancelling a CREATED order (BR-07, FR-UI-003). Same look as the shared
 * ConfirmDialog, plus the optional reason (up to 300 characters) that ConfirmDialog cannot hold.
 */
export function CancelOrderDialog({ open, order, onClose, onCancelled, onFailed }) {
  const [reason, setReason] = useState('');
  const mutation = useMutation({
    mutationFn: () => cancelOrder(order.id, reason),
    onSuccess: (result) => {
      setReason('');
      onCancelled?.(result);
    },
    onError: (error) => onFailed?.(error),
  });

  const tooLong = reason.length > REASON_MAX;
  const close = () => {
    if (mutation.isPending) return;
    mutation.reset();
    setReason('');
    onClose?.();
  };

  return (
    <Dialog open={open} onClose={close} aria-labelledby="cancel-order-title"
      PaperProps={{ sx: { borderRadius: tokens.radius.card, p: 1, maxWidth: 480, width: '100%' } }}>
      <DialogTitle id="cancel-order-title" sx={{ fontWeight: 700 }}>Cancel this order?</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {order ? `Order ${order.trackingNumber} will be cancelled. This cannot be undone.` : ''}
        </DialogContentText>
        <TextField
          label="Reason (optional)"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          multiline
          minRows={2}
          fullWidth
          error={tooLong}
          helperText={`${reason.length}/${REASON_MAX}`}
          inputProps={{ 'aria-describedby': 'cancel-reason-count' }}
          FormHelperTextProps={{ id: 'cancel-reason-count' }}
        />
        {mutation.isError && (
          <Alert severity="error" sx={{ mt: 2 }} role="alert">{cancelErrorMessage(mutation.error)}</Alert>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={close} disabled={mutation.isPending} sx={{ color: tokens.palette.mutedText }}>
          Keep order
        </Button>
        <Button
          variant="contained"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending || tooLong || !order}
          sx={{
            backgroundColor: tokens.palette.destructive,
            '&:hover': { backgroundColor: tokens.palette.destructiveHover },
          }}
        >
          {mutation.isPending ? 'Cancelling...' : 'Cancel order'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default CancelOrderDialog;
