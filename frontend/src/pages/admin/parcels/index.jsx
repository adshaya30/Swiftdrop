import React, { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Link from '@mui/material/Link';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import TextField from '@mui/material/TextField';
import { listAdminParcels } from '../../../api/adminParcels';
import { listZones } from '../../../api/adminPricing';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import StatusChip from '../../../components/StatusChip';
import { tokens } from '../../../theme/theme';
import { formatDateTime, formatMoney } from '../../../utils/format';
import { OWNER_TYPES, PARCEL_STATUSES } from './constants';

const EMPTY_FILTERS = {
  status: '', ownerType: '', from: '', to: '', q: '', originDistrictCode: '', destinationDistrictCode: '',
};

const SORTABLE = [
  { field: 'trackingNumber', label: 'Tracking number' },
  { field: 'status', label: 'Status' },
  { field: 'createdAt', label: 'Created' },
];

/**
 * FR-PAR-011: admin parcel search. Filters are applied with the Apply button so that typing does not fire a request
 * per keystroke. The server enforces ADMIN; the page only hides itself from other roles through the route table.
 */
export default function AdminParcelsPage() {
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [applied, setApplied] = useState(EMPTY_FILTERS);
  const [rangeError, setRangeError] = useState(null);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [sort, setSort] = useState({ field: 'createdAt', dir: 'desc' });

  const zonesQuery = useQuery({ queryKey: ['admin', 'zones'], queryFn: listZones });
  const districts = useMemo(
    () => (zonesQuery.data || []).flatMap((z) => z.districts || []).sort((a, b) => a.name.localeCompare(b.name)),
    [zonesQuery.data],
  );

  const parcelsQuery = useQuery({
    queryKey: ['admin', 'parcels', applied, page, size, sort],
    queryFn: () => listAdminParcels({ ...applied, page, size, sort: `${sort.field},${sort.dir}` }),
    placeholderData: keepPreviousData,
  });

  const setField = (name) => (event) => setDraft((d) => ({ ...d, [name]: event.target.value }));

  const applyFilters = (event) => {
    event.preventDefault();
    if (draft.from && draft.to && draft.from > draft.to) {
      setRangeError('The "from" date must not be after the "to" date.');
      return;
    }
    setRangeError(null);
    setApplied({ ...draft, q: draft.q.trim() });
    setPage(0);
  };

  const clearFilters = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setRangeError(null);
    setPage(0);
  };

  const toggleSort = (field) => {
    setSort((s) => (s.field === field ? { field, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { field, dir: 'asc' }));
    setPage(0);
  };

  const nativeSelect = { native: true };
  const items = parcelsQuery.data?.items || [];

  return (
    <Box>
      <PageHeader title="Parcels" subtitle="Search every parcel in the system." />

      <Card
        component="form"
        onSubmit={applyFilters}
        aria-label="Parcel filters"
        sx={{ p: 2, mb: 3, border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card }}
        elevation={0}
      >
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' } }}>
          <TextField
            label="Tracking number or merchant reference"
            value={draft.q}
            onChange={setField('q')}
            inputProps={{ maxLength: 64 }}
          />
          <TextField select label="Status" value={draft.status} onChange={setField('status')}
            SelectProps={nativeSelect} InputLabelProps={{ shrink: true }}>
            <option value="">All statuses</option>
            {PARCEL_STATUSES.map((s) => (
              <option key={s} value={s}>{tokens.status[s].label}</option>
            ))}
          </TextField>
          <TextField select label="Owner type" value={draft.ownerType} onChange={setField('ownerType')}
            SelectProps={nativeSelect} InputLabelProps={{ shrink: true }}>
            <option value="">All owners</option>
            {OWNER_TYPES.map((o) => (
              <option key={o.value} value={o.value}>{o.label}</option>
            ))}
          </TextField>
          <TextField select label="Origin district" value={draft.originDistrictCode}
            onChange={setField('originDistrictCode')} SelectProps={nativeSelect} InputLabelProps={{ shrink: true }}>
            <option value="">Any district</option>
            {districts.map((d) => (
              <option key={d.code} value={d.code}>{d.name}</option>
            ))}
          </TextField>
          <TextField select label="Destination district" value={draft.destinationDistrictCode}
            onChange={setField('destinationDistrictCode')} SelectProps={nativeSelect}
            InputLabelProps={{ shrink: true }}>
            <option value="">Any district</option>
            {districts.map((d) => (
              <option key={d.code} value={d.code}>{d.name}</option>
            ))}
          </TextField>
          <TextField label="Created from" type="date" value={draft.from} onChange={setField('from')}
            InputLabelProps={{ shrink: true }} error={Boolean(rangeError)} />
          <TextField label="Created to" type="date" value={draft.to} onChange={setField('to')}
            InputLabelProps={{ shrink: true }} error={Boolean(rangeError)} helperText={rangeError} />
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start' }}>
            <Button type="submit" variant="contained">Apply filters</Button>
            <Button type="button" onClick={clearFilters}>Clear</Button>
          </Box>
        </Box>
      </Card>

      {parcelsQuery.isPending && <LoadingState message="Loading parcels..." />}

      {parcelsQuery.isError && (
        <ErrorState
          message={parcelsQuery.error?.detail || 'Failed to load parcels.'}
          onRetry={() => parcelsQuery.refetch()}
        />
      )}

      {parcelsQuery.isSuccess && items.length === 0 && (
        <EmptyState title="No parcels found" description="No parcel matches the current filters." />
      )}

      {parcelsQuery.isSuccess && items.length > 0 && (
        <Card elevation={0} sx={{ border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card }}>
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Parcels">
              <TableHead>
                <TableRow>
                  {SORTABLE.slice(0, 2).map((col) => (
                    <TableCell key={col.field} sortDirection={sort.field === col.field ? sort.dir : false}>
                      <TableSortLabel active={sort.field === col.field} direction={sort.field === col.field ? sort.dir : 'asc'}
                        onClick={() => toggleSort(col.field)}>
                        {col.label}
                      </TableSortLabel>
                    </TableCell>
                  ))}
                  <TableCell>Owner</TableCell>
                  <TableCell>Merchant reference</TableCell>
                  <TableCell>Route</TableCell>
                  <TableCell align="right">Fee</TableCell>
                  <TableCell sortDirection={sort.field === 'createdAt' ? sort.dir : false}>
                    <TableSortLabel active={sort.field === 'createdAt'} direction={sort.field === 'createdAt' ? sort.dir : 'asc'}
                      onClick={() => toggleSort('createdAt')}>
                      Created
                    </TableSortLabel>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {items.map((p) => (
                  <TableRow key={p.id} hover>
                    <TableCell>
                      <Link component={RouterLink} to={`/admin/parcels/${p.id}`} underline="hover" sx={{ fontWeight: 600 }}>
                        {p.trackingNumber}
                      </Link>
                    </TableCell>
                    <TableCell><StatusChip status={p.status} /></TableCell>
                    <TableCell>{p.ownerType === 'MERCHANT' ? 'Merchant' : 'Customer'}</TableCell>
                    <TableCell>{p.merchantReference || '—'}</TableCell>
                    <TableCell>{p.pickup?.districtName} → {p.dropoff?.districtName}</TableCell>
                    <TableCell align="right">{formatMoney(p.fee?.total)}</TableCell>
                    <TableCell>{formatDateTime(p.createdAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={parcelsQuery.data?.totalItems || 0}
            page={page}
            rowsPerPage={size}
            rowsPerPageOptions={[10, 20, 50, 100]}
            onPageChange={(_e, p) => setPage(p)}
            onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
          />
        </Card>
      )}
    </Box>
  );
}
