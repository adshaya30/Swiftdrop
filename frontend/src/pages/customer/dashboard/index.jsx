import React, { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Box, Button, Stack } from '@mui/material';
import PageHeader from '../../../components/PageHeader';
import LoadingState from '../../../components/LoadingState';
import EmptyState from '../../../components/EmptyState';
import { listParcels } from '../../../api/customerParcels';
import ParcelCards from '../components/ParcelCards';
import CustomerRequestError from '../components/CustomerRequestError';
import { useCustomerRequest } from '../components/useCustomerRequest';

/** FR-PAR-015: these are display props, not invented summary API field names.
 * The summary endpoint/DTO is pending. Wire its agreed adapter here once published.
 */
export function DashboardCounts({ active, delivered }) {
  const count = (value) => Number.isSafeInteger(value) && value >= 0 ? value : 'Unavailable';
  return <dl className="customer-counts" aria-label="Parcel counts">
    <div><dt>Active parcels</dt><dd>{count(active)}</dd></div>
    <div><dt>Delivered parcels</dt><dd>{count(delivered)}</dd></div>
  </dl>;
}

export default function CustomerDashboardPage() {
  const [revision, setRevision] = useState(0);
  const request = useCallback(async () => {
    // Approved fallback while the optional summary contract is pending.
    const recent = await listParcels({ page: 0, size: 5, sort: 'createdAt,desc' });
    if (!Array.isArray(recent?.items)) throw new Error('Invalid recent parcels');
    return { items: recent.items.slice(0, 5) };
  }, []);
  const result = useCustomerRequest(request, 'customer-dashboard', revision);
  return <Box className="customer-page">
    <PageHeader title="Customer dashboard" />
    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
      <Button component={Link} to="/customer/parcels/new" variant="contained">New Delivery</Button>
      <Button component={Link} to="/track" variant="outlined">Track Parcel</Button>
      <Button component={Link} to="/customer/parcels">My Parcels</Button>
    </Stack>
    {result.status === 'loading' && <LoadingState message="Loading your dashboard…" />}
    {result.status === 'error' && <CustomerRequestError error={result.error} onRetry={() => setRevision((value) => value + 1)} />}
    {result.status === 'success' && <>
      <DashboardCounts />
      <Alert severity="info">Parcel counts are unavailable. Your most recent parcels are shown below.</Alert>
      <h2>Recent parcels</h2>
      {result.data.items.length ? <ParcelCards items={result.data.items} /> : <EmptyState title="No deliveries yet" description="Create your first delivery using New Delivery above." />}
    </>}
  </Box>;
}
