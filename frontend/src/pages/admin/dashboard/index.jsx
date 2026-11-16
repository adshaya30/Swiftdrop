import React from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import { getDashboardParcels } from '../../../api/adminReports';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import { tokens } from '../../../theme/theme';
import { PlaceholderTile, SectionBoundary } from '../parcels/detail/optionalModules';

/**
 * Cards published by other members (master 7.4): DispatchCard (#7) and PendingMerchantsCard (#3). `import.meta.glob`
 * resolves to an empty object while a file does not exist, so the build never breaks and each card appears as soon
 * as its owner's pull request is merged.
 */
const first = (modules) => Object.values(modules)[0];
const dispatchModule = first(import.meta.glob('/src/pages/admin/dispatch/DispatchCard.jsx', { eager: true }));
const merchantsModule = first(import.meta.glob('/src/pages/admin/merchants/PendingMerchantsCard.jsx', { eager: true }));
const DispatchCardFromOwner = dispatchModule?.default ?? dispatchModule?.DispatchCard;
const PendingMerchantsCardFromOwner = merchantsModule?.default ?? merchantsModule?.PendingMerchantsCard;

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card, height: '100%' };

function Stat({ label, value }) {
  return (
    <Box>
      <Typography variant="h4" sx={{ fontWeight: 700, color: tokens.palette.text }}>{value}</Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>{label}</Typography>
    </Box>
  );
}

/** FR-PAR-013: parcels delivered and failed today (Asia/Colombo). Loads, fails and retries on its own. */
export function ParcelsTodayCard() {
  const query = useQuery({
    queryKey: ['admin', 'dashboard', 'parcels'],
    queryFn: getDashboardParcels,
    refetchInterval: 60000,
  });

  return (
    <Card elevation={0} sx={cardSx} component="section" aria-label="Parcels today">
      <CardContent>
        <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem', mb: 2 }}>Parcels today</Typography>
        {query.isPending && <LoadingState message="Loading..." minHeight={100} />}
        {query.isError && (
          <ErrorState message={query.error?.detail || 'Failed to load today\'s parcels.'}
            onRetry={() => query.refetch()} minHeight={100} />
        )}
        {query.isSuccess && (
          <Box sx={{ display: 'flex', gap: 4 }}>
            <Stat label="Delivered today" value={query.data.deliveredToday} />
            <Stat label="Failed today" value={query.data.failedToday} />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

/** A sibling card, or a placeholder tile until its owner's component is merged. A crash stays inside its own tile. */
function OwnerCard({ Component, title, placeholder }) {
  return (
    <Box component="section" aria-label={title}>
      <SectionBoundary title={title}>
        {Component ? <Component /> : <PlaceholderTile title={`${title} not available yet`} message={placeholder} />}
      </SectionBoundary>
    </Box>
  );
}

/**
 * Three independent cards (SRS 7.6): each has its own loading and error state, and a failure of one never blanks the
 * others. The sibling cards are inputs so that they can be tested; the page passes the real ones.
 */
export function DashboardView({ DispatchCard, PendingMerchantsCard }) {
  return (
    <Box>
      <PageHeader title="Dashboard" subtitle="Today at a glance." />
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
        <SectionBoundary title="Parcels today">
          <ParcelsTodayCard />
        </SectionBoundary>
        <OwnerCard Component={DispatchCard} title="Dispatch"
          placeholder="The unassigned parcels card appears here once the dispatch module is available." />
        <OwnerCard Component={PendingMerchantsCard} title="Pending merchants"
          placeholder="The pending merchants card appears here once the merchants module is available." />
      </Box>
    </Box>
  );
}

export default function AdminDashboardPage() {
  return <DashboardView DispatchCard={DispatchCardFromOwner} PendingMerchantsCard={PendingMerchantsCardFromOwner} />;
}
