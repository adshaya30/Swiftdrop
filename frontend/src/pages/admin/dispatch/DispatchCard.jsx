import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import { getDispatchDashboard } from '../../../api/adminDelivery';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import { tokens } from '../../../theme/theme';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card, height: '100%' };

function Stat({ label, value }) {
  return (
    <Box>
      <Typography variant="h4" sx={{ fontWeight: 700, color: tokens.palette.text }}>{value}</Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>{label}</Typography>
    </Box>
  );
}

export function DispatchCard() {
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'dispatch'],
    queryFn: getDispatchDashboard,
    refetchInterval: 60000,
  });

  return (
    <Card elevation={0} sx={cardSx} component="section" aria-label="Dispatch">
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem', mb: 2 }}>Dispatch</Typography>
        {query.isPending && <LoadingState message="Loading..." minHeight={100} />}
        {query.isError && (
          <ErrorState
            message={query.error?.detail || 'Failed to load dispatch totals.'}
            onRetry={() => query.refetch()}
            minHeight={100}
          />
        )}
        {query.isSuccess && (
          <>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
              <Stat label="Unassigned" value={query.data.unassigned} />
              <Stat label="In progress" value={query.data.inProgress} />
              <Stat label="Active couriers" value={query.data.activeCouriers} />
            </Box>
            <Button component={RouterLink} to="/admin/parcels/unassigned" variant="outlined" sx={{ mt: 2 }}>
              Open dispatch queue
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}

export default DispatchCard;
