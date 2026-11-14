import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import { listCourierDeliveries, listCouriers } from '../../../api/adminDelivery';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import StatusChip from '../../../components/StatusChip';
import { tokens } from '../../../theme/theme';
import { formatDateTime } from '../../../utils/format';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card };
const ACTIVE_STATUSES = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];
const SCOPES = [
  { value: 'today', label: 'Today' },
  { value: 'active', label: 'Active' },
  { value: 'completed', label: 'Completed' },
];

function courierStatusLabel(status) {
  if (status === 'ACTIVE') return 'Active';
  if (status === 'DEACTIVATED') return 'Deactivated';
  return status || 'Unknown';
}

function CourierStatus({ status }) {
  const active = status === 'ACTIVE';
  return (
    <Chip
      size="small"
      label={courierStatusLabel(status)}
      role="status"
      sx={{
        fontWeight: 600,
        color: active ? '#166534' : tokens.palette.text,
        backgroundColor: active ? '#DCFCE7' : tokens.palette.mutedSurface,
        border: `1px solid ${active ? '#86EFAC' : tokens.palette.border}`,
      }}
    />
  );
}

function statusLabel(status) {
  return status.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function activeCount(activeDeliveries = {}) {
  return ACTIVE_STATUSES.reduce((total, status) => total + (Number(activeDeliveries[status]) || 0), 0);
}

function CourierWorkloadsTable({ couriers, onSelectCourier, selectedCourierId }) {
  return (
    <Card elevation={0} sx={cardSx}>
      <TableContainer sx={{ overflowX: 'auto' }}>
        <Table size="small" aria-label="Courier workloads">
          <TableHead>
            <TableRow>
              <TableCell>Courier</TableCell>
              <TableCell>Vehicle</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Active deliveries</TableCell>
              <TableCell>Active delivery counts by status</TableCell>
              <TableCell align="right">Delivered today</TableCell>
              <TableCell align="right">Failed today</TableCell>
              <TableCell align="right">Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {couriers.map((courier) => {
              const statusCounts = courier.activeDeliveries || {};
              const workload = activeCount(statusCounts);
              const countsText = ACTIVE_STATUSES
                .map((status) => `${statusLabel(status)}: ${Number(statusCounts[status]) || 0}`)
                .join(' · ');

              return (
                <TableRow key={courier.id} hover selected={selectedCourierId === courier.id} sx={{ verticalAlign: 'top' }}>
                  <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{courier.fullName || '—'}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Typography variant="body2">{courier.vehicleType || '—'}</Typography>
                    <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                      {courier.vehicleNumber || '—'}
                    </Typography>
                  </TableCell>
                  <TableCell><CourierStatus status={courier.status} /></TableCell>
                  <TableCell align="right">{workload}</TableCell>
                  <TableCell sx={{ minWidth: 260 }}>{countsText}</TableCell>
                  <TableCell align="right">{courier.deliveredToday ?? '—'}</TableCell>
                  <TableCell align="right">{courier.failedToday ?? '—'}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      aria-label={`View deliveries for ${courier.fullName || 'courier'}`}
                      aria-pressed={selectedCourierId === courier.id}
                      onClick={() => onSelectCourier(courier)}
                    >
                      View Deliveries
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </Card>
  );
}

function CourierDeliveries({ courier, scope, onScopeChange, page, size, onPageChange, onSizeChange }) {
  const deliveriesQuery = useQuery({
    queryKey: ['admin', 'courier-deliveries', courier.id, scope, page, size],
    queryFn: () => listCourierDeliveries(courier.id, { scope, page, size }),
    enabled: Boolean(courier.id),
  });
  const items = deliveriesQuery.data?.items || [];

  return (
    <Card elevation={0} sx={cardSx} component="section" aria-label={`${courier.fullName || 'Courier'} deliveries`}>
      <CardContent sx={{ p: { xs: 1.5, sm: 2.5 } }}>
        <Typography variant="h6" sx={{ fontSize: '1rem', fontWeight: 700, mb: 1 }}>
          Deliveries for {courier.fullName || 'courier'}
        </Typography>
        <Tabs
          value={scope}
          onChange={(_event, nextScope) => onScopeChange(nextScope)}
          aria-label="Courier delivery scope"
          variant="fullWidth"
          sx={{ borderBottom: `1px solid ${tokens.palette.border}`, mb: 2 }}
        >
          {SCOPES.map(({ value, label }) => (
            <Tab key={value} value={value} label={label} sx={{ minWidth: 0 }} />
          ))}
        </Tabs>

        {deliveriesQuery.isPending && <LoadingState message="Loading courier deliveries..." minHeight={160} />}
        {deliveriesQuery.isError && (
          <ErrorState
            message={deliveriesQuery.error?.detail || 'Failed to load courier deliveries.'}
            onRetry={() => deliveriesQuery.refetch()}
            minHeight={180}
          />
        )}
        {deliveriesQuery.isSuccess && items.length === 0 && (
          <EmptyState
            title={`No ${scope} deliveries`}
            description="This courier has no deliveries in the selected scope."
            minHeight={180}
          />
        )}
        {deliveriesQuery.isSuccess && items.length > 0 && (
          <Card elevation={0} sx={cardSx}>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label={`${scope} deliveries for ${courier.fullName || 'courier'}`}>
                <TableHead>
                  <TableRow>
                    <TableCell>Tracking number</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Route</TableCell>
                    <TableCell>Assigned</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {items.map((delivery) => (
                    <TableRow key={delivery.parcelId} hover>
                      <TableCell sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                        {delivery.trackingNumber || '—'}
                      </TableCell>
                      <TableCell><StatusChip status={delivery.status} /></TableCell>
                      <TableCell sx={{ minWidth: 180 }}>
                        {[delivery.pickupCity, delivery.originDistrictName].filter(Boolean).join(', ') || '—'}
                        {' → '}
                        {[delivery.dropoffCity, delivery.destinationDistrictName].filter(Boolean).join(', ') || '—'}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(delivery.assignedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              component="div"
              count={deliveriesQuery.data?.totalItems || 0}
              page={page}
              rowsPerPage={size}
              rowsPerPageOptions={[10, 20, 50, 100]}
              onPageChange={(_event, nextPage) => onPageChange(nextPage)}
              onRowsPerPageChange={(event) => onSizeChange(Number(event.target.value))}
            />
          </Card>
        )}
      </CardContent>
    </Card>
  );
}

export default function AdminCouriersPage() {
  const [selectedCourier, setSelectedCourier] = useState(null);
  const [scope, setScope] = useState('today');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const couriersQuery = useQuery({
    queryKey: ['admin', 'couriers'],
    queryFn: listCouriers,
    refetchInterval: 30000,
  });

  const couriers = useMemo(
    () => [...(couriersQuery.data || [])].sort((a, b) => {
      const byWorkload = activeCount(a.activeDeliveries || {}) - activeCount(b.activeDeliveries || {});
      return byWorkload || (a.fullName || '').localeCompare(b.fullName || '');
    }),
    [couriersQuery.data],
  );

  const selectCourier = (courier) => {
    setSelectedCourier(courier);
    setScope('today');
    setPage(0);
  };

  return (
    <Box>
      <PageHeader title="Courier workloads" subtitle="Active delivery counts and courier delivery history." />

      {couriersQuery.isPending && <LoadingState message="Loading courier workloads..." />}
      {couriersQuery.isError && (
        <ErrorState
          message={couriersQuery.error?.detail || 'Failed to load courier workloads.'}
          onRetry={() => couriersQuery.refetch()}
        />
      )}
      {couriersQuery.isSuccess && couriers.length === 0 && (
        <EmptyState title="No couriers found" description="There are no couriers available to display." />
      )}
      {couriersQuery.isSuccess && couriers.length > 0 && (
        <>
          <CourierWorkloadsTable
            couriers={couriers}
            onSelectCourier={selectCourier}
            selectedCourierId={selectedCourier?.id}
          />
          {selectedCourier && (
            <Box sx={{ mt: 2 }}>
              <CourierDeliveries
                courier={selectedCourier}
                scope={scope}
                onScopeChange={(nextScope) => {
                  setScope(nextScope);
                  setPage(0);
                }}
                page={page}
                size={size}
                onPageChange={setPage}
                onSizeChange={(nextSize) => {
                  setSize(nextSize);
                  setPage(0);
                }}
              />
            </Box>
          )}
        </>
      )}
    </Box>
  );
}
