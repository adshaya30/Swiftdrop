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
import { applyServerErrors } from '../../../utils/format';

const zoneName = z.string().trim().min(2, 'Enter at least 2 characters').max(60, 'Use at most 60 characters');

/** FR-PRC-006: zone codes match [A-Z_]{2,20} and are immutable; names are 2-60 characters (zones.name VARCHAR(60)). */
export const createZoneSchema = z.object({
  code: z.string().regex(/^[A-Z_]{2,20}$/, 'Use 2 to 20 capital letters or underscores'),
  name: zoneName,
});
export const renameZoneSchema = z.object({ name: zoneName });

/** Form dialog: validation errors and server errors are shown without losing the input. */
function FormDialog({ open, title, intro, schema, defaultValues, submitLabel, onSubmit, onClose, children }) {
  const form = useForm({ resolver: zodResolver(schema), defaultValues });
  const { handleSubmit, reset, setError, formState: { errors, isSubmitting } } = form;

  useEffect(() => {
    if (open) reset(defaultValues);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const submit = handleSubmit(async (values) => {
    try {
      await onSubmit(values);
      onClose();
    } catch (err) {
      applyServerErrors(setError, err);
    }
  });

  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : onClose} fullWidth maxWidth="xs" aria-labelledby="zone-dialog-title">
      <form onSubmit={submit} noValidate>
        <DialogTitle id="zone-dialog-title" sx={{ fontWeight: 700 }}>{title}</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          {intro && <DialogContentText>{intro}</DialogContentText>}
          {errors.root?.serverError && <Alert severity="error">{errors.root.serverError.message}</Alert>}
          {children(form)}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button type="button" onClick={onClose} disabled={isSubmitting}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={isSubmitting}>{submitLabel}</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}

/** FR-PRC-006: create a zone. A duplicate code or name comes back as 400 and is shown on its field. */
export function CreateZoneDialog({ open, onSubmit, onClose }) {
  return (
    <FormDialog open={open} title="Create zone" schema={createZoneSchema} defaultValues={{ code: '', name: '' }}
      submitLabel="Create zone" onSubmit={onSubmit} onClose={onClose}>
      {({ register, formState: { errors } }) => (
        <>
          <TextField label="Code" required inputProps={{ maxLength: 20 }}
            error={Boolean(errors.code)}
            helperText={errors.code?.message || 'Capital letters and underscores. Cannot be changed later.'}
            {...register('code')} />
          <TextField label="Name" required inputProps={{ maxLength: 60 }}
            error={Boolean(errors.name)} helperText={errors.name?.message} {...register('name')} />
        </>
      )}
    </FormDialog>
  );
}

/** FR-PRC-006: rename a zone; the code stays as it is. */
export function RenameZoneDialog({ zone, onSubmit, onClose }) {
  return (
    <FormDialog open={Boolean(zone)} title="Rename zone" intro={zone ? `Zone code ${zone.code} cannot be changed.` : null}
      schema={renameZoneSchema} defaultValues={{ name: zone?.name || '' }} submitLabel="Save name"
      onSubmit={onSubmit} onClose={onClose}>
      {({ register, formState: { errors } }) => (
        <TextField label="Name" required inputProps={{ maxLength: 60 }}
          error={Boolean(errors.name)} helperText={errors.name?.message} {...register('name')} />
      )}
    </FormDialog>
  );
}

/** FR-PRC-006: a district belongs to exactly one zone. Only active zones other than the current one are offered. */
export function MoveDistrictDialog({ target, zones, onSubmit, onClose }) {
  const options = target ? zones.filter((z) => z.active && z.id !== target.zone.id) : [];
  const [zoneId, setZoneId] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setZoneId(options[0]?.id || '');
    setError(null);
    setBusy(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit(zoneId);
      onClose();
    } catch (err) {
      setError(err?.errors?.[0]?.message || err?.detail || 'The district could not be moved.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={Boolean(target)} onClose={busy ? undefined : onClose} fullWidth maxWidth="xs" aria-labelledby="move-title">
      <form onSubmit={submit}>
        <DialogTitle id="move-title" sx={{ fontWeight: 700 }}>Move district</DialogTitle>
        <DialogContent sx={{ display: 'grid', gap: 2, pt: '8px !important' }}>
          <DialogContentText>
            {target && `Move ${target.district.name} out of ${target.zone.name}. Later quotes use the new zone's prices; `}
            {target && 'existing parcels keep their fee. If there is no price for the new zone pair, quotes for this district fail until one is set.'}
          </DialogContentText>
          {error && <Alert severity="error">{error}</Alert>}
          {options.length === 0 ? (
            <Alert severity="info">There is no other active zone to move this district to.</Alert>
          ) : (
            <TextField select label="New zone" value={zoneId} onChange={(e) => setZoneId(e.target.value)}
              SelectProps={{ native: true }} InputLabelProps={{ shrink: true }}>
              {options.map((z) => <option key={z.id} value={z.id}>{z.name}</option>)}
            </TextField>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button type="button" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={busy || options.length === 0}>Move district</Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
