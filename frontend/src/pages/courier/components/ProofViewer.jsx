import React, { useState, useEffect } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import NotesOutlinedIcon from '@mui/icons-material/NotesOutlined';
import AccessTimeOutlinedIcon from '@mui/icons-material/AccessTimeOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { getProof, getProofPhoto } from '../../../api/proofs';
import { formatDateTime } from '../../../utils/format';
import { tokens } from '../../../theme/theme';

/**
 * Proof of Delivery viewer component (SRS FR-DEL-007, BR-06, FR-UI-011).
 * Reusable across customer, merchant, courier, and admin detail views.
 * Loads photo binary via apiBlob with Bearer token and creates a revokable Blob URL.
 */
export function ProofViewer({ parcelId }) {
  const [proof, setProof] = useState(null);
  const [photoUrl, setPhotoUrl] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;
    let createdUrl = null;

    async function loadProofData() {
      if (!parcelId) return;
      setLoading(true);
      setError(null);

      try {
        const proofData = await getProof(parcelId);
        if (!isMounted) return;
        setProof(proofData);

        if (proofData?.hasPhoto) {
          try {
            const blob = await getProofPhoto(parcelId);
            if (!isMounted) return;
            createdUrl = URL.createObjectURL(blob);
            setPhotoUrl(createdUrl);
          } catch (photoErr) {
            console.error('Failed to load proof photo:', photoErr);
          }
        }
      } catch (err) {
        if (!isMounted) return;
        if (err?.status !== 404) {
          setError(err?.detail || 'Failed to load delivery proof.');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadProofData();

    return () => {
      isMounted = false;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [parcelId]);

  if (loading) {
    return (
      <Card
        sx={{
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          p: 2,
        }}
      >
        <Skeleton variant="text" width="50%" height={28} sx={{ mb: 1 }} />
        <Skeleton variant="rectangular" height={140} sx={{ borderRadius: tokens.radius.control }} />
      </Card>
    );
  }

  if (error) {
    return (
      <Alert severity="warning" sx={{ borderRadius: tokens.radius.control }}>
        {error}
      </Alert>
    );
  }

  if (!proof) {
    return null;
  }

  return (
    <Card
      sx={{
        borderRadius: tokens.radius.card,
        border: `1px solid ${tokens.palette.border}`,
        backgroundColor: tokens.palette.surface,
        boxShadow: 'none',
      }}
    >
      <CardContent sx={{ p: { xs: 2, sm: 2.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <CheckCircleOutlineIcon sx={{ color: tokens.status.DELIVERED.text }} />
          <Typography variant="subtitle1" sx={{ fontWeight: 700, color: tokens.palette.text }}>
            Proof of Delivery
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {/* Recipient Name */}
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
            <PersonOutlineIcon sx={{ color: tokens.palette.mutedText, fontSize: 20, mt: 0.3 }} />
            <Box>
              <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                Received by
              </Typography>
              <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                {proof.recipientName}
              </Typography>
            </Box>
          </Box>

          {/* Delivered At */}
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
            <AccessTimeOutlinedIcon sx={{ color: tokens.palette.mutedText, fontSize: 20, mt: 0.3 }} />
            <Box>
              <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                Delivered At
              </Typography>
              <Typography variant="body2" sx={{ color: tokens.palette.text }}>
                {formatDateTime(proof.capturedAt)}
              </Typography>
            </Box>
          </Box>

          {/* Courier Note (if present) */}
          {proof.note && (
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
              <NotesOutlinedIcon sx={{ color: tokens.palette.mutedText, fontSize: 20, mt: 0.3 }} />
              <Box>
                <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                  Delivery Note
                </Typography>
                <Typography variant="body2" sx={{ color: tokens.palette.text, fontStyle: 'italic' }}>
                  "{proof.note}"
                </Typography>
              </Box>
            </Box>
          )}

          {/* Photo Preview (FR-UI-011) */}
          {proof.hasPhoto && (
            <Box sx={{ mt: 1 }}>
              <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block', mb: 0.5 }}>
                Signature / Delivery Photo
              </Typography>
              {photoUrl ? (
                <Box
                  component="img"
                  src={photoUrl}
                  alt="Proof of Delivery"
                  sx={{
                    width: '100%',
                    maxHeight: 320,
                    objectFit: 'contain',
                    backgroundColor: tokens.palette.mutedSurface,
                    borderRadius: tokens.radius.control,
                    border: `1px solid ${tokens.palette.border}`,
                  }}
                />
              ) : (
                <Skeleton variant="rectangular" height={180} sx={{ borderRadius: tokens.radius.control }} />
              )}
            </Box>
          )}
        </Box>
      </CardContent>
    </Card>
  );
}

export default ProofViewer;

