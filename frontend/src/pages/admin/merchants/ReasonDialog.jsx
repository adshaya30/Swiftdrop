import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import { tokens } from '../../../theme/theme';
import { applyServerErrors } from '../../../utils/format';

/**
 * Confirmation dialog with a required reason of 10-300 characters (reject and suspend, BR-12, FR-AUTH-017).
 * It follows the shared ConfirmDialog (same look, destructive styling, loading state) and adds the reason field,
 * because the shared dialog has no input. `onSubmit(reason)` must return a promise; when it rejects, the error is shown
 * inside the dialog and the typed reason is kept.
 */

export const REASON_LIMITS = { min: 10, max: 300 };

export const reasonSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, 'A reason is required')
    .min(REASON_LIMITS.min, `The reason must be at least ${REASON_LIMITS.min} characters`)
    .max(REASON_LIMITS.max, `The reason must not exceed ${REASON_LIMITS.max} characters`),
});

export default function ReasonDialog({ open, title, message, confirmText, onSubmit, onCancel, onStale }) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(reasonSchema), defaultValues: { reason: '' } });

  const length = (watch('reason') || '').length;

  const cancel = () => {
    reset({ reason: '' });
    onCancel();
  };

  const submit = async ({ reason }) => {
    try {
      await onSubmit(reason);
      reset({ reason: '' });
    } catch (err) {
      if (err?.code === 'INVALID_STATUS_TRANSITION' && onStale) {
        reset({ reason: '' });
        onStale(err);
      } else {
        applyServerErrors(setError, err?.status ? err : { detail: 'The server could not be reached. Please try again.' });
      }
    }
  };

  return (
    <Dialog
      open={open}
      onClose={isSubmitting ? undefined : cancel}
      fullWidth
      maxWidth="xs"
      aria-labelledby="reason-dialog-title"
      PaperProps={{ sx: { borderRadius: tokens.radius.card } }}
    >
      <DialogTitle id="reason-dialog-title" sx={{ fontWeight: 700, color: tokens.palette.text }}>
        {title}
      </DialogTitle>
      <Box component="form" onSubmit={handleSubmit(submit)} noValidate aria-label={title}>
        <DialogContent sx={{ display: 'grid', gap: 2 }}>
          <DialogContentText sx={{ color: tokens.palette.mutedText }}>{message}</DialogContentText>
          {errors.root?.serverError && (
            <Alert severity="error" sx={{ borderRadius: tokens.radius.control }}>
              {errors.root.serverError.message}
            </Alert>
          )}
          <Box>
            <Box
              component="label"
              htmlFor="status-reason"
              sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.5, color: tokens.palette.text }}
            >
              Reason
            </Box>
            <TextField
              id="status-reason"
              multiline
              minRows={3}
              fullWidth
              size="small"
              autoFocus
              disabled={isSubmitting}
              error={Boolean(errors.reason)}
              helperText={errors.reason?.message || `At least ${REASON_LIMITS.min} characters`}
              {...register('reason')}
            />
            <Box sx={{ textAlign: 'right', fontSize: '0.75rem', color: tokens.palette.mutedText, mt: 0.5 }}>
              {length} / {REASON_LIMITS.max}
            </Box>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={cancel} disabled={isSubmitting} sx={{ color: tokens.palette.mutedText }}>
            Cancel
          </Button>
          <Button
            type="submit"
            variant="contained"
            disabled={isSubmitting}
            sx={{
              backgroundColor: tokens.palette.destructive,
              '&:hover': { backgroundColor: tokens.palette.destructiveHover },
            }}
          >
            {isSubmitting ? 'Processing...' : confirmText}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
