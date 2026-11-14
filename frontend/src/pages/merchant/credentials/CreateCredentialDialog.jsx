import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import { tokens } from '../../../theme/theme';
import { createCredential } from '../../../api/merchantAccount';
import { applyServerErrors } from '../../../utils/format';

/**
 * Create an API credential. SRS v2.1 - FR-AUTH-018.
 *
 * Phase 1: the label (1-50 characters). Phase 2: the client ID and the client secret, shown ONCE. The secret lives only
 * in this component's state (never in browser storage, the TanStack query or mutation cache, or the URL) and is
 * cleared when the dialog closes. In phase 2 the dialog cannot be dismissed by Escape or a click outside, so the secret is not lost by accident.
 */

export const labelSchema = z.object({
  label: z.string().trim().min(1, 'Label is required').max(50, 'Label must not exceed 50 characters'),
});

const LIMIT_MESSAGE = 'You already have 2 active credentials, which is the maximum. Revoke one first, then try again.';
const NETWORK_ERROR = 'The server could not be reached. Please check your connection and try again.';
const inputSx = { '& .MuiOutlinedInput-root': { minHeight: tokens.controlHeight.standard } };

async function writeToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

function SecretField({ id, label, value, copyLabel, copied, onCopy }) {
  return (
    <Box>
      <Typography component="label" htmlFor={id} sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.5 }}>
        {label}
      </Typography>
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'stretch' }}>
        <TextField
          id={id}
          value={value}
          size="small"
          fullWidth
          sx={inputSx}
          slotProps={{ htmlInput: { readOnly: true, spellCheck: false, autoComplete: 'off' } }}
        />
        <Button
          variant="outlined"
          onClick={onCopy}
          startIcon={<ContentCopyOutlinedIcon />}
          aria-label={copyLabel}
          sx={{ whiteSpace: 'nowrap', minHeight: tokens.controlHeight.standard }}
        >
          {copied ? 'Copied' : 'Copy'}
        </Button>
      </Box>
    </Box>
  );
}

export default function CreateCredentialDialog({ open, onClose }) {
  const queryClient = useQueryClient();
  const [created, setCreated] = useState(null); // { id, clientId, clientSecret, label } while the secret is shown
  const [copied, setCopied] = useState({ clientId: false, clientSecret: false });
  const [copyFailed, setCopyFailed] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: zodResolver(labelSchema), defaultValues: { label: '' } });

  const closeAndForget = () => {
    setCreated(null); // the secret leaves memory here
    setCopied({ clientId: false, clientSecret: false });
    setCopyFailed(false);
    reset({ label: '' });
    onClose();
  };

  const onSubmit = async ({ label }) => {
    try {
      const result = await createCredential(label); // called directly: useMutation would cache the secret
      setCreated(result);
      queryClient.invalidateQueries({ queryKey: ['merchant', 'credentials'] });
    } catch (err) {
      if (err?.code === 'CREDENTIAL_LIMIT_REACHED') {
        setError('root.serverError', { type: 'server', message: LIMIT_MESSAGE });
        queryClient.invalidateQueries({ queryKey: ['merchant', 'credentials'] });
      } else {
        applyServerErrors(setError, err?.status ? err : { detail: NETWORK_ERROR });
      }
    }
  };

  const copy = async (key, text) => {
    const ok = await writeToClipboard(text);
    setCopyFailed(!ok);
    setCopied((previous) => ({ ...previous, [key]: ok }));
  };

  const submitting = isSubmitting;
  const showingSecret = Boolean(created);

  return (
    <Dialog
      open={open}
      onClose={(_event, reason) => {
        if (showingSecret || submitting) return; // keep the secret on screen until "Done" is pressed
        if (reason) closeAndForget();
      }}
      disableEscapeKeyDown={showingSecret || submitting}
      fullWidth
      maxWidth="sm"
      aria-labelledby="create-credential-title"
      PaperProps={{ sx: { borderRadius: tokens.radius.card } }}
    >
      <DialogTitle id="create-credential-title" sx={{ fontWeight: 700 }}>
        {showingSecret ? 'Save your client secret' : 'Create API credential'}
      </DialogTitle>

      {showingSecret ? (
        <>
          <DialogContent sx={{ display: 'grid', gap: 2 }}>
            <Alert severity="warning" sx={{ borderRadius: tokens.radius.control }}>
              <strong>Copy your client secret now.</strong> You will not see it again. If you lose it, revoke this
              credential and create a new one.
            </Alert>
            <SecretField
              id="credential-client-id"
              label="Client ID"
              value={created.clientId}
              copyLabel="Copy client ID"
              copied={copied.clientId}
              onCopy={() => copy('clientId', created.clientId)}
            />
            <SecretField
              id="credential-client-secret"
              label="Client secret"
              value={created.clientSecret}
              copyLabel="Copy client secret"
              copied={copied.clientSecret}
              onCopy={() => copy('clientSecret', created.clientSecret)}
            />
            <Box role="status" aria-live="polite" sx={{ minHeight: 20, fontSize: '0.8125rem', color: tokens.palette.mutedText }}>
              {copyFailed
                ? 'Copying failed. Select the text in the field and copy it manually.'
                : copied.clientSecret || copied.clientId
                  ? 'Copied to the clipboard.'
                  : ''}
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button
              variant="contained"
              onClick={closeAndForget}
              sx={{
                backgroundColor: tokens.palette.primary,
                minHeight: tokens.controlHeight.standard,
                px: 4,
                fontWeight: 600,
                '&:hover': { backgroundColor: tokens.palette.primaryHover },
              }}
            >
              I have saved the secret
            </Button>
          </DialogActions>
        </>
      ) : (
        <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate aria-label="Create credential">
          <DialogContent sx={{ display: 'grid', gap: 2 }}>
            {errors.root?.serverError && (
              <Alert severity="error" sx={{ borderRadius: tokens.radius.control }}>
                {errors.root.serverError.message}
              </Alert>
            )}
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
              Give the credential a name that tells you which system uses it. You can have at most 2 active credentials.
            </Typography>
            <Box>
              <Typography component="label" htmlFor="credential-label" sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.5 }}>
                Label
              </Typography>
              <TextField
                id="credential-label"
                size="small"
                fullWidth
                autoFocus
                placeholder="e.g. Production store"
                disabled={submitting}
                error={Boolean(errors.label)}
                helperText={errors.label?.message}
                sx={inputSx}
                {...register('label')}
              />
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={closeAndForget} disabled={submitting} sx={{ color: tokens.palette.mutedText }}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={submitting}
              sx={{
                backgroundColor: tokens.palette.primary,
                minHeight: tokens.controlHeight.standard,
                px: 3,
                fontWeight: 600,
                '&:hover': { backgroundColor: tokens.palette.primaryHover },
              }}
            >
              {submitting ? 'Creating...' : 'Create credential'}
            </Button>
          </DialogActions>
        </Box>
      )}
    </Dialog>
  );
}
