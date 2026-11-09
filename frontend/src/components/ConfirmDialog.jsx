import React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import { tokens } from '../theme/theme';

export function ConfirmDialog({
  open,
  title = 'Confirm Action',
  message = 'Are you sure you want to proceed?',
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  isDestructive = false,
  onConfirm,
  onCancel,
  loading = false,
}) {
  return (
    <Dialog
      open={open}
      onClose={loading ? undefined : onCancel}
      PaperProps={{
        sx: {
          borderRadius: tokens.radius.card,
          p: 1,
          maxWidth: 440,
        },
      }}
    >
      <DialogTitle sx={{ fontWeight: 700, color: tokens.palette.text }}>
        {title}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ color: tokens.palette.mutedText }}>
          {message}
        </DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button
          onClick={onCancel}
          disabled={loading}
          sx={{ color: tokens.palette.mutedText }}
        >
          {cancelText}
        </Button>
        <Button
          onClick={onConfirm}
          disabled={loading}
          variant="contained"
          sx={{
            backgroundColor: isDestructive ? tokens.palette.destructive : tokens.palette.primary,
            '&:hover': {
              backgroundColor: isDestructive ? tokens.palette.destructiveHover : tokens.palette.primaryHover,
            },
          }}
        >
          {loading ? 'Processing...' : confirmText}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default ConfirmDialog;
