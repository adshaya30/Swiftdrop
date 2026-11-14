import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import { tokens } from '../../../theme/theme';
import LoadingState from '../../../components/LoadingState';
import ErrorState from '../../../components/ErrorState';
import { pendingCount } from '../../../api/adminMerchants';
import { PENDING_COUNT_QUERY_KEY } from './queryKeys';

/**
 * Dashboard card "Pending merchants" (FR-AUTH-024, SRS 7.6). Imported by Member 5's admin dashboard. It loads, fails and
 * retries on its own, so a failure never blanks the other dashboard cards. Kept light on purpose: the dashboard loads
 * this file eagerly.
 */
export function PendingMerchantsCard() {
  const query = useQuery({
    queryKey: PENDING_COUNT_QUERY_KEY,
    queryFn: pendingCount,
    refetchInterval: 60000,
  });

  return (
    <Card
      elevation={0}
      component="section"
      aria-label="Pending merchants"
      sx={{ border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card, height: '100%' }}
    >
      <CardContent>
        <Typography variant="h6" component="h2" sx={{ fontWeight: 700, fontSize: '1rem', mb: 2, color: tokens.palette.text }}>
          Pending merchants
        </Typography>

        {query.isPending && <LoadingState message="Loading..." minHeight={100} />}

        {query.isError && (
          <ErrorState
            message={query.error?.detail || 'Failed to load the pending merchants.'}
            onRetry={() => query.refetch()}
            minHeight={100}
          />
        )}

        {query.isSuccess && (
          <Box>
            <Typography variant="h4" sx={{ fontWeight: 700, color: tokens.palette.text }}>
              {query.data}
            </Typography>
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 2 }}>
              {query.data === 0
                ? 'No merchants are waiting for approval'
                : query.data === 1
                  ? 'merchant waiting for approval'
                  : 'merchants waiting for approval'}
            </Typography>
            <Button
              component={RouterLink}
              to="/admin/merchants?status=PENDING"
              variant="outlined"
              sx={{
                minHeight: tokens.controlHeight.standard,
                fontWeight: 600,
                color: tokens.palette.primaryPressed,
                borderColor: tokens.palette.primary,
              }}
            >
              {query.data === 0 ? 'View merchants' : 'Review pending merchants'}
            </Button>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

export default PendingMerchantsCard;
