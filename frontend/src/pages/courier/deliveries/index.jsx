import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Alert from '@mui/material/Alert';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import AccessTimeIcon from '@mui/icons-material/AccessTime';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';
import TwoWheelerIcon from '@mui/icons-material/TwoWheeler';
import { listDeliveries } from '../../../api/courier';
import { StatusChip } from '../../../components/StatusChip';
import LoadingState from '../../../components/LoadingState';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import { formatDateTime } from '../../../utils/format';
import { tokens } from '../../../theme/theme';

export function CourierDeliveriesPage() {
  const navigate = useNavigate();
  const [scope, setScope] = useState('today');
  const [deliveries, setDeliveries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchDeliveries = useCallback(async (currentScope, showLoading = false) => {
    if (showLoading) setLoading(true);
    setError(null);
    try {
      const data = await listDeliveries(currentScope, 0, 100);
      setDeliveries(data?.items || []);
    } catch (err) {
      setError(err?.detail || 'Failed to load deliveries. Please try again.');
    } finally {
      setLoading(false);
    }
  }, []);

  // Fetch when scope changes
  useEffect(() => {
    fetchDeliveries(scope, true);
  }, [scope, fetchDeliveries]);

  // Refetch every 30 s and on window focus (SRS FR-DEL-003)
  useEffect(() => {
    const interval = setInterval(() => {
      fetchDeliveries(scope, false);
    }, 30000);

    const onFocus = () => {
      fetchDeliveries(scope, false);
    };

    window.addEventListener('focus', onFocus);

    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', onFocus);
    };
  }, [scope, fetchDeliveries]);

  const handleScopeChange = (event, newScope) => {
    if (newScope !== null) {
      setScope(newScope);
    }
  };

  // Compute metrics for header
  const todayCount = deliveries.length;
  const nextAssigned = deliveries.find(
    (d) => d.status !== 'DELIVERED' && d.status !== 'FAILED' && d.status !== 'CANCELLED'
  );

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, pb: 4 }}>
      {/* Header with Today's Count & Next Delivery */}
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Typography variant="h5" sx={{ fontWeight: 800, color: tokens.palette.text }}>
          My Deliveries
        </Typography>
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
          {scope === 'today'
            ? `${todayCount} ${todayCount === 1 ? 'delivery' : 'deliveries'} assigned today`
            : `${todayCount} ${todayCount === 1 ? 'delivery' : 'deliveries'} in ${scope}`}
        </Typography>
      </Box>

      {/* Next Delivery Banner (if active) */}
      {nextAssigned && scope !== 'completed' && (
        <Card
          onClick={() => navigate(`/courier/deliveries/${nextAssigned.parcelId}`)}
          sx={{
            cursor: 'pointer',
            backgroundColor: tokens.palette.primarySoft,
            border: `1.5px solid ${tokens.palette.primary}`,
            borderRadius: tokens.radius.card,
            boxShadow: 'none',
            transition: 'transform 0.15s ease',
            '&:hover': {
              transform: 'translateY(-2px)',
            },
          }}
        >
          <CardContent sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <TwoWheelerIcon sx={{ color: tokens.palette.primary }} />
                <Typography variant="caption" sx={{ fontWeight: 700, color: tokens.palette.primaryText, textTransform: 'uppercase' }}>
                  Next Stop
                </Typography>
              </Box>
              <StatusChip status={nextAssigned.status} size="small" />
            </Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 800, color: tokens.palette.text, fontFamily: 'monospace' }}>
              {nextAssigned.trackingNumber}
            </Typography>
            <Typography variant="body2" sx={{ color: tokens.palette.text, mt: 0.5, fontWeight: 500 }}>
              {nextAssigned.pickupCity} → {nextAssigned.dropoffCity}
            </Typography>
          </CardContent>
        </Card>
      )}

      {/* Scope Switcher: Today / Active / Completed */}
      <Box sx={{ width: '100%' }}>
        <ToggleButtonGroup
          value={scope}
          exclusive
          onChange={handleScopeChange}
          fullWidth
          aria-label="Delivery scope"
          sx={{
            backgroundColor: tokens.palette.mutedSurface,
            p: 0.5,
            borderRadius: tokens.radius.card,
            border: `1px solid ${tokens.palette.border}`,
            '& .MuiToggleButton-root': {
              borderRadius: tokens.radius.control,
              border: 'none',
              py: 1,
              minHeight: 44, // 44px touch target
              fontWeight: 600,
              fontSize: '0.875rem',
              color: tokens.palette.mutedText,
              textTransform: 'none',
              '&.Mui-selected': {
                backgroundColor: tokens.palette.surface,
                color: tokens.palette.primary,
                boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
                '&:hover': {
                  backgroundColor: tokens.palette.surface,
                },
              },
            },
          }}
        >
          <ToggleButton value="today" aria-label="today deliveries">
            Today
          </ToggleButton>
          <ToggleButton value="active" aria-label="active deliveries">
            Active
          </ToggleButton>
          <ToggleButton value="completed" aria-label="completed deliveries">
            Completed
          </ToggleButton>
        </ToggleButtonGroup>
      </Box>

      {/* Loading State */}
      {loading && <LoadingState message="Updating deliveries..." minHeight="30vh" />}

      {/* Error State */}
      {!loading && error && (
        <ErrorState
          title="Could not load deliveries"
          message={error}
          onRetry={() => fetchDeliveries(scope, true)}
        />
      )}

      {/* Empty State */}
      {!loading && !error && deliveries.length === 0 && (
        <EmptyState
          title="No Deliveries Found"
          message={
            scope === 'today'
              ? 'You have no assigned deliveries for today yet.'
              : `No deliveries found under '${scope}'.`
          }
        />
      )}

      {/* Delivery Cards List */}
      {!loading && !error && deliveries.length > 0 && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          {deliveries.map((delivery) => {
            const isCancelled = delivery.status === 'CANCELLED';

            return (
              <Card
                key={delivery.parcelId}
                onClick={() => navigate(`/courier/deliveries/${delivery.parcelId}`)}
                sx={{
                  cursor: 'pointer',
                  borderRadius: tokens.radius.card,
                  border: `1px solid ${tokens.palette.border}`,
                  backgroundColor: tokens.palette.surface,
                  boxShadow: 'none',
                  transition: 'border-color 0.15s ease, box-shadow 0.15s ease',
                  '&:hover': {
                    borderColor: tokens.palette.primary,
                    boxShadow: '0 2px 8px rgba(0,0,0,0.05)',
                  },
                }}
              >
                <CardContent sx={{ p: 2, '&:last-child': { pb: 2 } }}>
                  {/* Top row: Tracking number + Status chip */}
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5 }}>
                    <Typography variant="body1" sx={{ fontWeight: 700, fontFamily: 'monospace', color: tokens.palette.text }}>
                      {delivery.trackingNumber}
                    </Typography>
                    <StatusChip status={delivery.status} size="small" />
                  </Box>

                  {/* Cancelled Notice */}
                  {isCancelled && (
                    <Alert severity="error" sx={{ py: 0.5, px: 1.5, mb: 1.5, borderRadius: tokens.radius.control }}>
                      This delivery was cancelled
                    </Alert>
                  )}

                  {/* Origin -> Destination Districts */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                    <LocationOnOutlinedIcon sx={{ color: tokens.palette.mutedText, fontSize: 18 }} />
                    <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                      {delivery.originDistrictName}
                    </Typography>
                    <ArrowForwardIcon sx={{ color: tokens.palette.mutedText, fontSize: 14 }} />
                    <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                      {delivery.destinationDistrictName}
                    </Typography>
                  </Box>

                  {/* Pickup City -> Dropoff City */}
                  <Box sx={{ pl: 3.2, mb: 1.5 }}>
                    <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                      {delivery.pickupCity} → {delivery.dropoffCity}
                    </Typography>
                  </Box>

                  {/* Assigned At Time */}
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, pl: 0.5 }}>
                    <AccessTimeIcon sx={{ color: tokens.palette.mutedText, fontSize: 15 }} />
                    <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                      Assigned: {formatDateTime(delivery.assignedAt)}
                    </Typography>
                  </Box>
                </CardContent>
              </Card>
            );
          })}
        </Box>
      )}
    </Box>
  );
}

export default CourierDeliveriesPage;

