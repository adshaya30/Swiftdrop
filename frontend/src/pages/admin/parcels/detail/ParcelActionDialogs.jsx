import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import ConfirmDialog from '../../../../components/ConfirmDialog';
import { applyServerErrors } from '../../../../utils/format';
import { ADMIN_FAIL_REASONS, FAILURE_REASON_LABELS } from '../constants';

/** FR-PAR-011, BR-07: an admin cancel needs a reason of 10-300 characters (mirrors the server limits). */
export const cancelSchema = z.object({
  reason: z.string().trim().min(10, 'Enter at least 10 characters').max(300, 'Use at most 300 characters'),
});

/** FR-PAR-012, BR-16: reason PARCEL_DAMAGED or OTHER; the note is always required (5-500). */
export const failSchema = z.object({
  failureReason: z.enum(ADMIN_FAIL_REASONS, { errorMap: () => ({ message: 'Choose a reason' }) }),
  note: z.string().trim().min(5, 'Enter at least 5 characters').max(500, 'Use at most 500 characters'),
});

/**
 * Two steps for a destructive action: a form that validates input, then a ConfirmDialog (master 9).
 * `onSubmit(values)` must return a promise; a rejected ApiError is shown on the form without losing the input.
 */
function TwoStepDialog({ open, title, intro, confirmTitle, confirmMessage, confirmText, schema, defaultValues,
  onSubmit, onClose, children }) {
  const [step, setStep] = useState('form');
  const [pending, setPending] = useState(null);
  const [busy, setBusy] = useState(false);
  const form = useForm({ resolver: zodResolver(schema), defaultValues });
  const { handleSubmit, setError, reset, clearErrors, formState: { errors } } = form;

  useEffect(() => {
    if (open) {
      reset(defaultValues);
      setStep('form');
      setPending(null);
      setBusy(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const toConfirm = handleSubmit((values) => {
    clearErrors('root.serverError');
    setPending(values);
    setStep('confirm');
  });

  const confirm = async () => {
    setBusy(true);
    try {
      await onSubmit(pending);
      onClose();
    } catch (err) {
      applyServerErrors(setError, err);
      setStep('form');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <Dialog open={open && step === 'form'} onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="parcel-action-title">
        <form onSubmit={toConfirm} noValidate>
          <DialogTitle id="parcel-action-title" sx={{ fontWeight: 700 }}>{title}</DialogTitle>
          <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
            <DialogContentText>{intro}</DialogContentText>
            {errors.root?.serverError && <Alert severity="error">{errors.root.serverError.message}</Alert>}
            {children(form)}
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button type="button" onClick={onClose}>Back</Button>
            <Button type="submit" variant="contained">Continue</Button>
          </DialogActions>
        </form>
      </Dialog>
      <ConfirmDialog
        open={open && step === 'confirm'}
        title={confirmTitle}
        message={confirmMessage}
        confirmText={confirmText}
        isDestructive
        loading={busy}
        onConfirm={confirm}
        onCancel={() => setStep('form')}
      />
    </>
  );
}

/** FR-PAR-011: cancel a CREATED parcel. */
export function CancelParcelDialog({ open, trackingNumber, onSubmit, onClose }) {
  return (
    <TwoStepDialog
      open={open}
      title="Cancel parcel"
      intro={`Cancel parcel ${trackingNumber}. Only a parcel that has not been picked up can be cancelled.`}
      confirmTitle="Cancel this parcel?"
      confirmMessage={`Parcel ${trackingNumber} will be cancelled. This cannot be undone.`}
      confirmText="Yes, cancel parcel"
      schema={cancelSchema}
      defaultValues={{ reason: '' }}
      onSubmit={onSubmit}
      onClose={onClose}
    >
      {({ register, formState: { errors } }) => (
        <TextField
          label="Reason"
          multiline
          minRows={3}
          required
          inputProps={{ maxLength: 300 }}
          error={Boolean(errors.reason)}
          helperText={errors.reason?.message || '10 to 300 characters'}
          {...register('reason')}
        />
      )}
    </TwoStepDialog>
  );
}

/** FR-PAR-012: force-fail a parcel that is stuck after pickup. */
export function FailParcelDialog({ open, trackingNumber, onSubmit, onClose }) {
  return (
    <TwoStepDialog
      open={open}
      title="Mark parcel as failed"
      intro={`Mark parcel ${trackingNumber} as failed. The customer is notified and a merchant webhook is sent.`}
      confirmTitle="Mark this parcel as failed?"
      confirmMessage={`Parcel ${trackingNumber} will be marked as failed. This cannot be undone.`}
      confirmText="Yes, mark as failed"
      schema={failSchema}
      defaultValues={{ failureReason: 'PARCEL_DAMAGED', note: '' }}
      onSubmit={onSubmit}
      onClose={onClose}
    >
      {({ register, formState: { errors } }) => (
        <>
          <TextField
            select
            label="Failure reason"
            required
            SelectProps={{ native: true }}
            InputLabelProps={{ shrink: true }}
            error={Boolean(errors.failureReason)}
            helperText={errors.failureReason?.message}
            {...register('failureReason')}
          >
            {ADMIN_FAIL_REASONS.map((r) => (
              <option key={r} value={r}>{FAILURE_REASON_LABELS[r]}</option>
            ))}
          </TextField>
          <TextField
            label="Note"
            multiline
            minRows={3}
            required
            inputProps={{ maxLength: 500 }}
            error={Boolean(errors.note)}
            helperText={errors.note?.message || '5 to 500 characters. Do not enter customer or recipient contact details.'}
            {...register('note')}
          />
        </>
      )}
    </TwoStepDialog>
  );
}
