import React, { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import PageHeader from '../../../components/PageHeader';
import StatusChip from '../../../components/StatusChip';
import LoadingState from '../../../components/LoadingState';
import ErrorState from '../../../components/ErrorState';
import EmptyState from '../../../components/EmptyState';
import CancelOrderDialog, { cancelErrorMessage } from '../components/CancelOrderDialog';
import { listOrders } from '../../../api/merchantOrders';
import { formatDateTime, formatMoney } from '../../../utils/format';
import { tokens } from '../../../theme/theme';

const STATUSES = [
  ['CREATED', 'Created'], ['PICKED_UP', 'Picked up'], ['IN_TRANSIT', 'In transit'],
  ['OUT_FOR_DELIVERY', 'Out for delivery'], ['DELIVERED', 'Delivered'], ['FAILED', 'Delivery failed'],
  ['CANCELLED', 'Cancelled'],
];
export const SORTS = [
  ['createdAt,desc', 'Newest first'], ['createdAt,asc', 'Oldest first'],
  ['status,asc', 'Status'], ['trackingNumber,asc', 'Tracking number'],
];
const EMPTY_FILTERS = { status: '', from: '', to: '', merchantReference: '', q: '' };

/** Client checks mirror the server limits (FR-UI-004): reference up to 64, tracking prefix up to 13, from <= to. */
export function validateFilters(filters) {
  const errors = {};
  if (filters.merchantReference.length > 64) errors.merchantReference = 'At most 64 characters';
  if (filters.q.length > 13) errors.q = 'At most 13 characters';
  if (filters.from && filters.to && filters.from > filters.to) errors.to = 'Must be on or after the From date';
  return errors;
}

/** FR-PAR-008: the merchant's orders with filters, sorting, pagination and cancel while CREATED. */
export default function MerchantOrdersPage() {
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [errors, setErrors] = useState({});
  const [sort, setSort] = useState(SORTS[0][0]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [cancelling, setCancelling] = useState(null);
  const [notice, setNotice] = useState(null);

  const query = useQuery({
    queryKey: ['merchant', 'orders', filters, sort, page, size],
    queryFn: () => listOrders({ ...filters, sort, page, size }),
    placeholderData: keepPreviousData,
  });

  const change = (field) => (e) => setDraft((d) => ({ ...d, [field]: e.target.value }));
  const apply = (e) => {
    e.preventDefault();
    const found = validateFilters(draft);
    setErrors(found);
    if (Object.keys(found).length === 0) {
      setFilters(draft);
      setPage(0);
    }
  };
  const reset = () => {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
    setErrors({});
    setPage(0);
  };

  const items = query.data?.items ?? [];

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', width: '100%' }}>
      <PageHeader title="Orders" subtitle="Orders your store created through the SwiftDrop merchant API." />

      <Card component="form" role="search" aria-label="Filter orders" onSubmit={apply} sx={{ p: 2, mb: 2 }}>
        <Box sx={{
          display: 'grid', gap: 2,
          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', md: 'repeat(3, minmax(0, 1fr))' },
        }}>
          <TextField select label="Status" value={draft.status} onChange={change('status')}>
            <MenuItem value="">All statuses</MenuItem>
            {STATUSES.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
          </TextField>
          <TextField label="From" type="date" value={draft.from} onChange={change('from')}
            InputLabelProps={{ shrink: true }} />
          <TextField label="To" type="date" value={draft.to} onChange={change('to')}
            InputLabelProps={{ shrink: true }} error={Boolean(errors.to)} helperText={errors.to} />
          <TextField label="Merchant reference" value={draft.merchantReference} onChange={change('merchantReference')}
            error={Boolean(errors.merchantReference)} helperText={errors.merchantReference} />
          <TextField label="Tracking number starts with" value={draft.q} onChange={change('q')}
            error={Boolean(errors.q)} helperText={errors.q} />
          <TextField select label="Sort by" value={sort} onChange={(e) => { setSort(e.target.value); setPage(0); }}>
            {SORTS.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
          </TextField>
        </Box>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mt: 2 }}>
          <Button type="submit" variant="contained">Apply filters</Button>
          <Button type="button" onClick={reset}>Reset</Button>
        </Box>
      </Card>

      {notice && (
        <Alert severity={notice.severity} onClose={() => setNotice(null)} sx={{ mb: 2 }} role="status">
          {notice.text}
        </Alert>
      )}

      {query.isPending ? (
        <LoadingState message="Loading orders..." />
      ) : query.isError ? (
        <ErrorState title="Orders unavailable" message="We could not load your orders. Please try again."
          onRetry={() => query.refetch()} />
      ) : items.length === 0 ? (
        <EmptyState title="No orders found"
          description="No orders match these filters. Orders are created through the merchant API." />
      ) : (
        <Card>
          <TableContainer sx={{ maxWidth: '100%', overflowX: 'auto' }}>
            <Table aria-label="Orders" size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Tracking number</TableCell>
                  <TableCell>Reference</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Drop-off</TableCell>
                  <TableCell align="right">Fee</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell><Box component="span" sx={visuallyHidden}>Actions</Box></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {items.map((order) => (
                  <TableRow key={order.id} hover>
                    <TableCell>
                      <Link component={RouterLink} to={`/merchant/orders/${order.id}`} sx={{ fontWeight: 600 }}>
                        {order.trackingNumber}
                      </Link>
                    </TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>{order.merchantReference || '—'}</TableCell>
                    <TableCell><StatusChip status={order.status} /></TableCell>
                    <TableCell>{order.dropoff?.city}</TableCell>
                    <TableCell align="right">{formatMoney(order.fee?.total)}</TableCell>
                    <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                    <TableCell>
                      {order.status === 'CREATED' && (
                        <Button size="small" color="error" onClick={() => setCancelling(order)}
                          aria-label={`Cancel order ${order.trackingNumber}`} sx={{ minHeight: 44 }}>
                          Cancel
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={query.data.totalItems}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 20, 50]}
            onPageChange={(_, next) => setPage(next)}
            onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
            sx={{ borderTop: `1px solid ${tokens.palette.border}` }}
          />
        </Card>
      )}

      <CancelOrderDialog
        open={Boolean(cancelling)}
        order={cancelling}
        onClose={() => setCancelling(null)}
        onCancelled={(result) => {
          setCancelling(null);
          setNotice({ severity: 'success', text: `Order ${result.trackingNumber} was cancelled.` });
          query.refetch();
        }}
        onFailed={(error) => {
          if (error?.code === 'INVALID_STATUS_TRANSITION' || error?.code === 'CONCURRENT_UPDATE') {
            setCancelling(null);
            setNotice({ severity: 'warning', text: cancelErrorMessage(error) });
            query.refetch();
          }
        }}
      />
    </Box>
  );
}

const visuallyHidden = {
  border: 0, clip: 'rect(0 0 0 0)', height: 1, margin: -1, overflow: 'hidden', padding: 0,
  position: 'absolute', whiteSpace: 'nowrap', width: 1,
};
