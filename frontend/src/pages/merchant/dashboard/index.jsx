import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import PageHeader from '../../../components/PageHeader';
import StatusChip from '../../../components/StatusChip';
import LoadingState from '../../../components/LoadingState';
import ErrorState from '../../../components/ErrorState';
import EmptyState from '../../../components/EmptyState';
import { getDashboardSummary } from '../../../api/merchantOrders';
import { getWebhookSummary } from '../../../api/webhook';
import { formatDateTime } from '../../../utils/format';
import { tokens } from '../../../theme/theme';

/** FR-UI-008: the merchant dashboard refreshes every 30 s. */
export const REFRESH_MS = 30000;

const ACTIVE = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];
const ALL = [...ACTIVE, 'DELIVERED', 'FAILED', 'CANCELLED'];

const cardSx = { height: '100%' };
const gridSx = {
  display: 'grid',
  gap: 2,
  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' },
};

function SectionTitle({ id, children }) {
  return (
    <Typography id={id} component="h2" variant="h6" sx={{ mb: 1.5, fontSize: '1rem' }}>
      {children}
    </Typography>
  );
}

function StatusCounts({ id, title, counts, statuses }) {
  return (
    <Card component="section" aria-labelledby={id} sx={cardSx}>
      <CardContent>
        <SectionTitle id={id}>{title}</SectionTitle>
        <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0, display: 'grid', gap: 1 }}>
          {statuses.map((status) => (
            <Box component="li" key={status}
              sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1 }}>
              <StatusChip status={status} />
              <Typography sx={{ fontWeight: 700, color: tokens.palette.text }} data-testid={`${id}-${status}`}>
                {counts?.[status] ?? 0}
              </Typography>
            </Box>
          ))}
        </Box>
      </CardContent>
    </Card>
  );
}

/** Webhook delivery health from notification-service; its failure never breaks the rest of the page (SRS 7.6). */
function WebhookHealth() {
  const query = useQuery({
    queryKey: ['merchant', 'webhook-summary'],
    queryFn: getWebhookSummary,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
    retry: false,
  });

  const rows = [
    ['Failed (24 h)', query.data?.failed24h],
    ['Pending', query.data?.pending],
    ['Succeeded (24 h)', query.data?.succeeded24h],
  ];

  return (
    <Card component="section" aria-labelledby="webhook-health" sx={cardSx}>
      <CardContent>
        <SectionTitle id="webhook-health">Webhook deliveries</SectionTitle>
        {query.isError ? (
          <Typography role="status" sx={{ color: tokens.palette.mutedText }}>
            Webhook status unavailable
          </Typography>
        ) : (
          <Box component="dl" sx={{ m: 0, display: 'grid', gap: 1 }}>
            {rows.map(([label, value]) => (
              <Box key={label} sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
                <Typography component="dt" sx={{ color: tokens.palette.mutedText }}>{label}</Typography>
                <Typography component="dd" sx={{ m: 0, fontWeight: 700 }}>
                  {query.isPending ? '…' : (value ?? 0)}
                </Typography>
              </Box>
            ))}
          </Box>
        )}
        <Link component={RouterLink} to="/merchant/webhook" sx={{ display: 'inline-block', mt: 1.5 }}>
          Webhook settings and delivery log
        </Link>
      </CardContent>
    </Card>
  );
}

function RecentOrders({ orders }) {
  return (
    <Card component="section" aria-labelledby="recent-orders">
      <CardContent>
        <SectionTitle id="recent-orders">Recent orders</SectionTitle>
        {orders.length === 0 ? (
          <EmptyState title="No orders yet"
            description="Orders you create through the merchant API will appear here." minHeight={160} />
        ) : (
          <Box component="ul" sx={{ listStyle: 'none', p: 0, m: 0 }}>
            {orders.map((order) => (
              <Box component="li" key={order.id}
                sx={{
                  display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between',
                  gap: 1, py: 1.25, borderTop: `1px solid ${tokens.palette.border}`, '&:first-of-type': { borderTop: 0 },
                }}>
                <Box sx={{ minWidth: 0 }}>
                  <Link component={RouterLink} to={`/merchant/orders/${order.id}`} sx={{ fontWeight: 600 }}>
                    {order.trackingNumber}
                  </Link>
                  <Typography variant="body2" sx={{ color: tokens.palette.mutedText, overflowWrap: 'anywhere' }}>
                    {[order.merchantReference, order.dropoffCity, formatDateTime(order.createdAt)]
                      .filter(Boolean).join(' · ')}
                  </Typography>
                </Box>
                <StatusChip status={order.status} />
              </Box>
            ))}
          </Box>
        )}
        <Link component={RouterLink} to="/merchant/orders" sx={{ display: 'inline-block', mt: 1.5 }}>
          View all orders
        </Link>
      </CardContent>
    </Card>
  );
}

/** FR-PAR-014, FR-UI-008, SRS 7.6: merchant dashboard composed from parcel-service and notification-service. */
export default function MerchantDashboardPage() {
  const summary = useQuery({
    queryKey: ['merchant', 'dashboard-summary'],
    queryFn: getDashboardSummary,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
  });

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', width: '100%' }}>
      <PageHeader title="Merchant dashboard" subtitle="Your orders at a glance. Updated every 30 seconds." />

      <Alert severity="info" sx={{ mb: 3 }}>
        Orders are created by your store through the SwiftDrop merchant API, not on this website.{' '}
        <Link component={RouterLink} to="/merchant/credentials">Manage your API credentials</Link>.
      </Alert>

      {summary.isPending ? (
        <LoadingState message="Loading your dashboard..." />
      ) : summary.isError ? (
        <Box sx={{ display: 'grid', gap: 2 }}>
          <ErrorState title="Dashboard unavailable"
            message="We could not load your order summary. Please try again." onRetry={() => summary.refetch()} />
          <Box sx={gridSx}><WebhookHealth /></Box>
        </Box>
      ) : (
        <Box sx={{ display: 'grid', gap: 2 }}>
          <Box sx={gridSx}>
            <StatusCounts id="active-orders" title="Active orders" counts={summary.data.activeCounts}
              statuses={ACTIVE} />
            <StatusCounts id="last-30-days" title="Orders in the last 30 days" counts={summary.data.last30DaysCounts}
              statuses={ALL} />
            <WebhookHealth />
          </Box>
          <RecentOrders orders={summary.data.recentOrders ?? []} />
        </Box>
      )}
    </Box>
  );
}
