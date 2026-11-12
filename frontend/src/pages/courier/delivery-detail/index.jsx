import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import IconButton from '@mui/material/IconButton';
import PhoneIcon from '@mui/icons-material/Phone';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import { getDelivery, updateStatus } from '../../../api/courier';
import { StatusChip } from '../../../components/StatusChip';
import ConfirmDialog from '../../../components/ConfirmDialog';
import LoadingState from '../../../components/LoadingState';
import ErrorState from '../../../components/ErrorState';
import ProofViewer from '../components/ProofViewer';
import { tokens } from '../../../theme/theme';

const STATUS_ACTION_LABELS = {
  PICKED_UP: 'Mark picked up',
  IN_TRANSIT: 'Mark in transit',
  OUT_FOR_DELIVERY: 'Mark out for delivery',
  DELIVERED: 'Mark delivered',
};

export function CourierDeliveryDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [delivery, setDelivery] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionError, setActionError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Delivered Dialog State
  const [deliveredDialogOpen, setDeliveredDialogOpen] = useState(false);
  const [recipientName, setRecipientName] = useState('');
  const [deliveredNote, setDeliveredNote] = useState('');
  const [photoFile, setPhotoFile] = useState(null);
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState(null);
  const [deliveredErrors, setDeliveredErrors] = useState({});

  // Quick Action State (for non-delivered, non-failed transitions)
  const [quickActionTarget, setQuickActionTarget] = useState(null);
  const [quickActionNote, setQuickActionNote] = useState('');
  const [quickActionDialogOpen, setQuickActionDialogOpen] = useState(false);

  // Failed Dialog State
  const [failedDialogOpen, setFailedDialogOpen] = useState(false);
  const [confirmFailOpen, setConfirmFailOpen] = useState(false);
  const [failureReason, setFailureReason] = useState('');
  const [failureNote, setFailureNote] = useState('');
  const [failedErrors, setFailedErrors] = useState({});

  const loadDelivery = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const data = await getDelivery(id);
      setDelivery(data);
    } catch (err) {
      if (err?.status === 404) {
        setError('This delivery is not assigned to you or could not be found.');
      } else {
        setError(err?.detail || 'Failed to load delivery details.');
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadDelivery();
  }, [loadDelivery]);

  // Clean up photo preview blob URL on change/unmount
  useEffect(() => {
    return () => {
      if (photoPreviewUrl) {
        URL.revokeObjectURL(photoPreviewUrl);
      }
    };
  }, [photoPreviewUrl]);

  const handlePhotoSelect = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 10 * 1024 * 1024) {
        setDeliveredErrors((prev) => ({
          ...prev,
          photo: 'Photo exceeds maximum size of 10 MB',
        }));
        return;
      }
      setPhotoFile(file);
      if (photoPreviewUrl) URL.revokeObjectURL(photoPreviewUrl);
      setPhotoPreviewUrl(URL.createObjectURL(file));
      setDeliveredErrors((prev) => {
        const copy = { ...prev };
        delete copy.photo;
        return copy;
      });
    }
  };

  const handleClearPhoto = () => {
    setPhotoFile(null);
    if (photoPreviewUrl) {
      URL.revokeObjectURL(photoPreviewUrl);
      setPhotoPreviewUrl(null);
    }
  };

  const handleQuickAction = (targetStatus) => {
    setActionError(null);
    setQuickActionTarget(targetStatus);
    setQuickActionNote('');
    setQuickActionDialogOpen(true);
  };

  const submitQuickAction = async () => {
    if (!quickActionTarget) return;
    setSubmitting(true);
    setActionError(null);

    const formData = new FormData();
    formData.append('status', quickActionTarget);
    if (quickActionNote.trim()) {
      formData.append('note', quickActionNote.trim());
    }

    try {
      await updateStatus(id, formData);
      setQuickActionDialogOpen(false);
      await loadDelivery();
    } catch (err) {
      handleApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeliveredSubmit = async (e) => {
    e.preventDefault();
    setActionError(null);
    const errors = {};

    if (!recipientName.trim() || recipientName.trim().length < 2 || recipientName.trim().length > 100) {
      errors.recipientName = 'Recipient name must be between 2 and 100 characters';
    }
    if (deliveredNote && deliveredNote.length > 500) {
      errors.note = 'Note cannot exceed 500 characters';
    }

    if (Object.keys(errors).length > 0) {
      setDeliveredErrors(errors);
      return;
    }

    setSubmitting(true);
    const formData = new FormData();
    formData.append('status', 'DELIVERED');
    formData.append('recipientName', recipientName.trim());
    if (deliveredNote.trim()) {
      formData.append('note', deliveredNote.trim());
    }
    if (photoFile) {
      formData.append('photo', photoFile);
    }

    try {
      await updateStatus(id, formData);
      setDeliveredDialogOpen(false);
      await loadDelivery();
    } catch (err) {
      handleApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleFailedPrompt = () => {
    setActionError(null);
    const errors = {};
    if (!failureReason) {
      errors.failureReason = 'Please select a reason for the failed delivery';
    }
    if (failureReason === 'OTHER') {
      if (!failureNote.trim() || failureNote.trim().length < 5 || failureNote.trim().length > 500) {
        errors.note = 'A note between 5 and 500 characters is required for "Other reason"';
      }
    } else if (failureNote && failureNote.length > 500) {
      errors.note = 'Note cannot exceed 500 characters';
    }

    if (Object.keys(errors).length > 0) {
      setFailedErrors(errors);
      return;
    }

    // Open confirmation before submitting destructive failure report
    setConfirmFailOpen(true);
  };

  const submitFailed = async () => {
    setSubmitting(true);
    setActionError(null);

    const formData = new FormData();
    formData.append('status', 'FAILED');
    formData.append('failureReason', failureReason);
    if (failureNote.trim()) {
      formData.append('note', failureNote.trim());
    }

    try {
      await updateStatus(id, formData);
      setConfirmFailOpen(false);
      setFailedDialogOpen(false);
      await loadDelivery();
    } catch (err) {
      setConfirmFailOpen(false);
      handleApiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleApiError = (err) => {
    if (err?.status === 404) {
      alert('This delivery is no longer assigned to you.');
      navigate('/courier/deliveries');
    } else if (err?.code === 'INVALID_STATUS_TRANSITION' || err?.code === 'CONCURRENT_UPDATE') {
      setActionError(`${err.detail || 'The delivery status was updated.'} Refreshing latest state...`);
      loadDelivery();
    } else if (err?.code === 'PAYLOAD_TOO_LARGE') {
      setActionError('The attached photo is too large. Please use a photo under 10 MB.');
    } else if (err?.code === 'UNSUPPORTED_MEDIA_TYPE') {
      setActionError('Unsupported image format. Please select a valid JPEG or PNG photo.');
    } else {
      setActionError(err?.detail || 'An unexpected error occurred. Please try again.');
    }
  };

  if (loading) {
    return <LoadingState message="Loading delivery details..." minHeight="50vh" />;
  }

  if (error || !delivery) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/courier/deliveries')}
          sx={{ alignSelf: 'flex-start', color: tokens.palette.mutedText }}
        >
          Back to Deliveries
        </Button>
        <ErrorState
          title="Delivery Not Available"
          message={error || 'Unable to view this delivery.'}
          onRetry={loadDelivery}
        />
      </Box>
    );
  }

  const allowedNext = delivery.allowedNextStatuses || [];
  const allowedReasons = delivery.allowedFailureReasons || [];
  const primaryStatuses = allowedNext.filter((s) => s !== 'FAILED');
  const canReportFailed = allowedNext.includes('FAILED');

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pb: 6 }}>
      {/* Back button & Title */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/courier/deliveries')}
          sx={{ color: tokens.palette.mutedText, minHeight: 44, borderRadius: tokens.radius.control }}
        >
          Deliveries
        </Button>
        <StatusChip status={delivery.status} />
      </Box>

      {/* Safety Notice (Master 9 & 10) */}
      <Alert
        severity="info"
        icon={<WarningAmberIcon />}
        sx={{
          borderRadius: tokens.radius.control,
          backgroundColor: tokens.palette.mutedSurface,
          color: tokens.palette.text,
          border: `1px solid ${tokens.palette.border}`,
          py: 0.5,
          fontSize: '0.8125rem',
        }}
      >
        Update the status only when you have stopped safely.
      </Alert>

      {/* Error alert from actions */}
      {actionError && (
        <Alert severity="error" sx={{ borderRadius: tokens.radius.control }}>
          {actionError}
        </Alert>
      )}

      {/* Tracking Number Header */}
      <Box>
        <Typography variant="caption" sx={{ color: tokens.palette.mutedText, textTransform: 'uppercase', letterSpacing: 0.5 }}>
          Tracking Number
        </Typography>
        <Typography variant="h5" sx={{ fontWeight: 800, fontFamily: 'monospace', color: tokens.palette.text }}>
          {delivery.trackingNumber}
        </Typography>
      </Box>

      {/* STOP 1: Pickup Location & Contact */}
      <Card sx={{ borderRadius: tokens.radius.card, border: `1px solid ${tokens.palette.border}`, boxShadow: 'none' }}>
        <CardContent sx={{ p: { xs: 2, sm: 2.5 } }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: tokens.palette.primary, mb: 1.5, textTransform: 'uppercase' }}>
            1. Pickup Point
          </Typography>
          <Typography variant="body1" sx={{ fontWeight: 600, color: tokens.palette.text, mb: 0.5 }}>
            {delivery.pickup?.contactName}
          </Typography>
          {delivery.pickup?.contactPhone && (
            <Box sx={{ mb: 1.5 }}>
              <Link
                href={`tel:${delivery.pickup.contactPhone}`}
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.8,
                  fontWeight: 600,
                  color: tokens.palette.primary,
                  textDecoration: 'none',
                  minHeight: 44, // 44px tap target
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                <PhoneIcon fontSize="small" />
                {delivery.pickup.contactPhone}
              </Link>
            </Box>
          )}
          <Typography variant="body2" sx={{ color: tokens.palette.text, whiteSpace: 'pre-line' }}>
            {[
              delivery.pickup?.addressLine1,
              delivery.pickup?.addressLine2,
              delivery.pickup?.city,
              delivery.pickup?.districtName,
              delivery.pickup?.postalCode,
            ]
              .filter(Boolean)
              .join(', ')}
          </Typography>
        </CardContent>
      </Card>

      {/* STOP 2: Drop-off Location & Contact */}
      <Card sx={{ borderRadius: tokens.radius.card, border: `1px solid ${tokens.palette.border}`, boxShadow: 'none' }}>
        <CardContent sx={{ p: { xs: 2, sm: 2.5 } }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: tokens.palette.primary, mb: 1.5, textTransform: 'uppercase' }}>
            2. Delivery Point
          </Typography>
          <Typography variant="body1" sx={{ fontWeight: 600, color: tokens.palette.text, mb: 0.5 }}>
            {delivery.dropoff?.contactName}
          </Typography>
          {delivery.dropoff?.contactPhone && (
            <Box sx={{ mb: 1.5 }}>
              <Link
                href={`tel:${delivery.dropoff.contactPhone}`}
                sx={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 0.8,
                  fontWeight: 600,
                  color: tokens.palette.primary,
                  textDecoration: 'none',
                  minHeight: 44, // 44px tap target
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                <PhoneIcon fontSize="small" />
                {delivery.dropoff.contactPhone}
              </Link>
            </Box>
          )}
          <Typography variant="body2" sx={{ color: tokens.palette.text, whiteSpace: 'pre-line' }}>
            {[
              delivery.dropoff?.addressLine1,
              delivery.dropoff?.addressLine2,
              delivery.dropoff?.city,
              delivery.dropoff?.districtName,
              delivery.dropoff?.postalCode,
            ]
              .filter(Boolean)
              .join(', ')}
          </Typography>
        </CardContent>
      </Card>

      {/* Special Delivery Instructions */}
      {delivery.deliveryInstructions && (
        <Card sx={{ borderRadius: tokens.radius.card, backgroundColor: tokens.palette.mutedSurface, border: `1px solid ${tokens.palette.border}`, boxShadow: 'none' }}>
          <CardContent sx={{ p: 2 }}>
            <Typography variant="caption" sx={{ fontWeight: 700, color: tokens.palette.mutedText, textTransform: 'uppercase' }}>
              Delivery Instructions
            </Typography>
            <Typography variant="body2" sx={{ color: tokens.palette.text, mt: 0.5, fontWeight: 500 }}>
              {delivery.deliveryInstructions}
            </Typography>
          </CardContent>
        </Card>
      )}

      {/* Parcel Specifications */}
      <Card sx={{ borderRadius: tokens.radius.card, border: `1px solid ${tokens.palette.border}`, boxShadow: 'none' }}>
        <CardContent sx={{ p: 2 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 1 }}>
            Parcel Information
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 1.5 }}>
            <Box>
              <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                Weight
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                {delivery.parcel?.weightKg} kg
              </Typography>
            </Box>
            <Box>
              <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                Size Category
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                {delivery.parcel?.size}
              </Typography>
            </Box>
            {delivery.parcel?.description && (
              <Box sx={{ gridColumn: 'span 2' }}>
                <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                  Description
                </Typography>
                <Typography variant="body2" sx={{ color: tokens.palette.text }}>
                  {delivery.parcel.description}
                </Typography>
              </Box>
            )}
          </Box>
        </CardContent>
      </Card>

      {/* If DELIVERED: Show Proof of Delivery */}
      {delivery.status === 'DELIVERED' && (
        <ProofViewer parcelId={delivery.id} />
      )}

      {/* ACTION BUTTONS (Couriers primary actions: 48-56px high) */}
      {allowedNext.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, mt: 1 }}>
          {primaryStatuses.map((nextStatus) => {
            const isDelivered = nextStatus === 'DELIVERED';
            const label = STATUS_ACTION_LABELS[nextStatus] || `Mark ${nextStatus}`;

            return (
              <Button
                key={nextStatus}
                variant="contained"
                disabled={submitting}
                onClick={() => (isDelivered ? setDeliveredDialogOpen(true) : handleQuickAction(nextStatus))}
                sx={{
                  height: 52, // 48-56 px touch friendly
                  backgroundColor: tokens.palette.primary,
                  color: '#FFFFFF',
                  fontWeight: 700,
                  fontSize: '1rem',
                  borderRadius: tokens.radius.control,
                  '&:hover': {
                    backgroundColor: tokens.palette.primaryHover,
                  },
                }}
              >
                {label}
              </Button>
            );
          })}

          {/* Report Failed Delivery Button (Red Destructive) */}
          {canReportFailed && (
            <Button
              variant="outlined"
              disabled={submitting}
              onClick={() => {
                setFailureReason('');
                setFailureNote('');
                setFailedErrors({});
                setFailedDialogOpen(true);
              }}
              sx={{
                height: 48,
                color: tokens.palette.destructive,
                borderColor: tokens.palette.destructive,
                fontWeight: 700,
                borderRadius: tokens.radius.control,
                '&:hover': {
                  borderColor: tokens.palette.destructiveHover,
                  backgroundColor: 'rgba(220, 38, 38, 0.04)',
                },
              }}
            >
              Report failed delivery
            </Button>
          )}
        </Box>
      )}

      {/* QUICK STATUS UPDATE DIALOG (Picked Up, In Transit, Out for Delivery) */}
      <Dialog
        open={quickActionDialogOpen}
        onClose={submitting ? undefined : () => setQuickActionDialogOpen(false)}
        maxWidth="xs"
        fullWidth
        PaperProps={{ sx: { borderRadius: tokens.radius.card, p: 1 } }}
      >
        <DialogTitle sx={{ fontWeight: 700 }}>
          {quickActionTarget ? STATUS_ACTION_LABELS[quickActionTarget] : 'Update Status'}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 2 }}>
            Confirm moving this delivery to{' '}
            <strong>{quickActionTarget ? STATUS_ACTION_LABELS[quickActionTarget] : ''}</strong>.
          </Typography>
          <TextField
            label="Optional Note (visible to staff/admins)"
            fullWidth
            multiline
            rows={2}
            value={quickActionNote}
            onChange={(e) => setQuickActionNote(e.target.value)}
            helperText="Do not enter customer or recipient contact details (up to 500 chars)"
            disabled={submitting}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setQuickActionDialogOpen(false)} disabled={submitting}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={submitQuickAction}
            disabled={submitting}
            sx={{
              backgroundColor: tokens.palette.primary,
              '&:hover': { backgroundColor: tokens.palette.primaryHover },
            }}
          >
            {submitting ? 'Updating...' : 'Confirm'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* MARK DELIVERED DIALOG */}
      <Dialog
        open={deliveredDialogOpen}
        onClose={submitting ? undefined : () => setDeliveredDialogOpen(false)}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { borderRadius: tokens.radius.card, p: 1 } }}
      >
        <DialogTitle sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1 }}>
          <CheckCircleOutlineIcon sx={{ color: tokens.status.DELIVERED.text }} />
          Confirm Delivery
        </DialogTitle>
        <form onSubmit={handleDeliveredSubmit}>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <TextField
              label="Recipient Name *"
              required
              fullWidth
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
              error={!!deliveredErrors.recipientName}
              helperText={deliveredErrors.recipientName || 'Name of the person who received the parcel (2-100 characters)'}
              disabled={submitting}
            />

            <TextField
              label="Delivery Note (Optional)"
              fullWidth
              multiline
              rows={2}
              value={deliveredNote}
              onChange={(e) => setDeliveredNote(e.target.value)}
              error={!!deliveredErrors.note}
              helperText={deliveredErrors.note || 'Do not enter customer or recipient contact details (max 500 characters)'}
              disabled={submitting}
            />

            {/* Photo Capture / Upload */}
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
              <Typography variant="caption" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                Proof Photo (Optional, JPEG or PNG, max 10 MB)
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <Button
                  component="label"
                  variant="outlined"
                  startIcon={<PhotoCameraIcon />}
                  disabled={submitting}
                  sx={{ minHeight: 44, borderRadius: tokens.radius.control }}
                >
                  Capture / Choose Photo
                  <input
                    type="file"
                    accept="image/*"
                    capture="environment"
                    hidden
                    onChange={handlePhotoSelect}
                  />
                </Button>
                {photoFile && (
                  <IconButton onClick={handleClearPhoto} color="error" title="Remove photo">
                    <DeleteOutlineIcon />
                  </IconButton>
                )}
              </Box>
              {deliveredErrors.photo && (
                <Typography variant="caption" sx={{ color: tokens.palette.destructive }}>
                  {deliveredErrors.photo}
                </Typography>
              )}

              {photoPreviewUrl && (
                <Box
                  component="img"
                  src={photoPreviewUrl}
                  alt="Preview"
                  sx={{
                    width: '100%',
                    maxHeight: 200,
                    objectFit: 'contain',
                    mt: 1,
                    borderRadius: tokens.radius.control,
                    border: `1px solid ${tokens.palette.border}`,
                  }}
                />
              )}
            </Box>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2 }}>
            <Button onClick={() => setDeliveredDialogOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={submitting}
              sx={{
                backgroundColor: tokens.status.DELIVERED.text,
                '&:hover': { backgroundColor: '#15803D' },
                minWidth: 120,
              }}
            >
              {submitting ? 'Submitting...' : 'Mark Delivered'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* REPORT FAILED DELIVERY DIALOG */}
      <Dialog
        open={failedDialogOpen}
        onClose={submitting ? undefined : () => setFailedDialogOpen(false)}
        maxWidth="sm"
        fullWidth
        PaperProps={{ sx: { borderRadius: tokens.radius.card, p: 1 } }}
      >
        <DialogTitle sx={{ fontWeight: 700, color: tokens.palette.destructive, display: 'flex', alignItems: 'center', gap: 1 }}>
          <ErrorOutlineIcon />
          Report Delivery Failure
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <TextField
            select
            label="Reason for Failure *"
            required
            fullWidth
            value={failureReason}
            onChange={(e) => setFailureReason(e.target.value)}
            error={!!failedErrors.failureReason}
            helperText={failedErrors.failureReason || 'Select a valid reason for this stage'}
            disabled={submitting}
          >
            {allowedReasons.map((r) => (
              <MenuItem key={r.code} value={r.code}>
                {r.label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            label={failureReason === 'OTHER' ? 'Reason Details *' : 'Optional Explanation'}
            required={failureReason === 'OTHER'}
            fullWidth
            multiline
            rows={3}
            value={failureNote}
            onChange={(e) => setFailureNote(e.target.value)}
            error={!!failedErrors.note}
            helperText={
              failedErrors.note ||
              (failureReason === 'OTHER'
                ? 'Required: Explain reason (5-500 chars). Do not enter contact details.'
                : 'Do not enter customer or recipient contact details (up to 500 chars).')
            }
            disabled={submitting}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setFailedDialogOpen(false)} disabled={submitting}>
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={handleFailedPrompt}
            disabled={submitting}
            sx={{
              backgroundColor: tokens.palette.destructive,
              '&:hover': { backgroundColor: tokens.palette.destructiveHover },
            }}
          >
            Continue
          </Button>
        </DialogActions>
      </Dialog>

      {/* CONFIRM FAILED DIALOG */}
      <ConfirmDialog
        open={confirmFailOpen}
        title="Confirm Delivery Failure"
        message="Are you sure you want to report this delivery as failed? This will record the failure and notify the customer."
        confirmText="Report Failure"
        cancelText="Go Back"
        isDestructive
        loading={submitting}
        onConfirm={submitFailed}
        onCancel={() => setConfirmFailOpen(false)}
      />
    </Box>
  );
}

export default CourierDeliveryDetailPage;

