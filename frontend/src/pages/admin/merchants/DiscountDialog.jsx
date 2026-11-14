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
 * Edit the merchant discount: 0.00 to 50.00 with at most two decimals (FR-AUTH-017). The value is validated as text so
 * that no floating point rounding hides a third decimal; it is sent as a number. `onSubmit(percent)` must return a
 * promise; its errors are shown in the dialog and the input is kept.
 */

export const DISCOUNT_PATTERN = /^\d{1,2}(\.\d{1,2})?$/;

export const discountSchema = z.object({
  discountPercent: z
    .string()
    .trim()
    .min(1, 'Enter a discount between 0 and 50')
    .refine((value) => DISCOUNT_PATTERN.test(value), 'Use a number with at most two decimals, for example 12.5')
    .refine((value) => !DISCOUNT_PATTERN.test(value) || Number(value) <= 50, 'The discount cannot be more than 50'),
});

export default function DiscountDialog({ open, currentPercent, onSubmit, onCancel }) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(discountSchema),
    values: { discountPercent: String(Number(currentPercent ?? 0)) },
  });

  const cancel = () => {
    reset();
    onCancel();
  };

  const submit = async ({ discountPercent }) => {
    try {
      await onSubmit(Number(discountPercent));
    } catch (err) {
      applyServerErrors(setError, err?.status ? err : { detail: 'The server could not be reached. Please try again.' });
    }
  };

  return (
    <Dialog
      open={open}
      onClose={isSubmitting ? undefined : cancel}
      fullWidth
      maxWidth="xs"
      aria-labelledby="discount-dialog-title"
      PaperProps={{ sx: { borderRadius: tokens.radius.card } }}
    >
      <DialogTitle id="discount-dialog-title" sx={{ fontWeight: 700, color: tokens.palette.text }}>
        Edit discount
      </DialogTitle>
      <Box component="form" onSubmit={handleSubmit(submit)} noValidate aria-label="Edit discount">
        <DialogContent sx={{ display: 'grid', gap: 2 }}>
          <DialogContentText sx={{ color: tokens.palette.mutedText }}>
            The discount is taken off the delivery fee of every order this merchant creates. It can be between 0 and 50
            percent.
          </DialogContentText>
          {errors.root?.serverError && (
            <Alert severity="error" sx={{ borderRadius: tokens.radius.control }}>
              {errors.root.serverError.message}
            </Alert>
          )}
          <Box>
            <Box
              component="label"
              htmlFor="discount-percent"
              sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.5, color: tokens.palette.text }}
            >
              Discount (%)
            </Box>
            <TextField
              id="discount-percent"
              size="small"
              fullWidth
              autoFocus
              disabled={isSubmitting}
              error={Boolean(errors.discountPercent)}
              helperText={errors.discountPercent?.message || 'Between 0 and 50, up to two decimals'}
              slotProps={{ htmlInput: { inputMode: 'decimal', autoComplete: 'off' } }}
              {...register('discountPercent')}
            />
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
              backgroundColor: tokens.palette.primary,
              '&:hover': { backgroundColor: tokens.palette.primaryHover },
            }}
          >
            {isSubmitting ? 'Saving...' : 'Save discount'}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
