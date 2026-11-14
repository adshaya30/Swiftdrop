import React, { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Box, Button, Stack, TextField } from '@mui/material';
import PageHeader from '../../../../components/PageHeader';
import LoadingState from '../../../../components/LoadingState';
import EmptyState from '../../../../components/EmptyState';
import { tokens } from '../../../../theme/theme';
import { listParcels } from '../../../../api/customerParcels';
import ParcelCards from '../../components/ParcelCards';
import CustomerRequestError from '../../components/CustomerRequestError';
import { useCustomerRequest } from '../../components/useCustomerRequest';

const defaults = { q: '', status: '', from: '', to: '', sortField: 'createdAt', direction: 'desc', size: '20' };

export default function CustomerParcelsPage() {
  const [draft, setDraft] = useState(defaults);
  const [query, setQuery] = useState({ page: 0, size: 20, sort: 'createdAt,desc' });
  const [revision, setRevision] = useState(0);
  const [validation, setValidation] = useState('');
  const request = useCallback(async () => {
    const data = await listParcels(query);
    if (!Array.isArray(data?.items) || !Number.isInteger(data.totalPages) || !Number.isInteger(data.totalItems)) throw new Error('Invalid parcel list');
    return data;
  }, [query]);
  const result = useCustomerRequest(request, JSON.stringify(query), revision);
  const update = (field) => (event) => setDraft((value) => ({ ...value, [field]: event.target.value }));
  const apply = (event) => {
    event.preventDefault();
    if (draft.from && draft.to && draft.from > draft.to) {
      setValidation('From date must be on or before to date.');
      return;
    }
    setValidation('');
    const filters = Object.fromEntries(['q', 'status', 'from', 'to'].map((key) => [key, draft[key].trim()]).filter(([, value]) => value));
    setQuery({ ...filters, page: 0, size: Number(draft.size), sort: `${draft.sortField},${draft.direction}` });
    setRevision((value) => value + 1);
  };
  const select = (label, field, children) => <TextField label={label} select SelectProps={{ native: true }} value={draft[field]} onChange={update(field)} fullWidth>{children}</TextField>;

  return <Box className="customer-page">
    <PageHeader title="My parcels" action={<Button component={Link} to="/customer/parcels/new" variant="contained">New Delivery</Button>} />
    <form onSubmit={apply} aria-label="Parcel filters">
      <div className="customer-filter-grid">
        <TextField label="Tracking-number prefix" value={draft.q} onChange={update('q')} inputProps={{ maxLength: 13 }} fullWidth />
        {select('Status', 'status', <><option value="">All statuses</option>{Object.entries(tokens.status).map(([status, config]) => <option key={status} value={status}>{config.label}</option>)}</>)}
        <TextField label="From date" type="date" value={draft.from} onChange={update('from')} InputLabelProps={{ shrink: true }} fullWidth />
        <TextField label="To date" type="date" value={draft.to} onChange={update('to')} InputLabelProps={{ shrink: true }} fullWidth />
        {select('Sort by', 'sortField', <><option value="createdAt">Creation date</option><option value="status">Status</option><option value="trackingNumber">Tracking number</option></>)}
        {select('Sort direction', 'direction', <><option value="desc">Descending</option><option value="asc">Ascending</option></>)}
        {select('Parcels per page', 'size', [20, 50, 100].map((size) => <option key={size} value={size}>{size}</option>))}
      </div>
      {validation && <Alert severity="error" role="alert">{validation}</Alert>}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button type="submit" variant="contained">Apply filters</Button>
        <Button type="button" onClick={() => { setDraft(defaults); setValidation(''); setQuery({ page: 0, size: 20, sort: 'createdAt,desc' }); setRevision((value) => value + 1); }}>Reset filters</Button>
      </Stack>
    </form>
    <section aria-label="Parcel results" aria-busy={result.status === 'loading'}>
      {result.status === 'loading' && <LoadingState message="Loading your parcels…" />}
      {result.status === 'error' && <CustomerRequestError error={result.error} onRetry={() => setRevision((value) => value + 1)} />}
      {result.status === 'success' && <>
        <p role="status">{result.data.totalItems} parcels · Page {result.data.totalPages ? query.page + 1 : 0} of {result.data.totalPages}</p>
        {result.data.items.length ? <ParcelCards items={result.data.items} /> : <EmptyState title="No parcels found" description="Try different filters or create a new delivery." />}
        <Stack component="nav" aria-label="Parcel pagination" direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button disabled={query.page === 0} onClick={() => setQuery((value) => ({ ...value, page: value.page - 1 }))}>Previous page</Button>
          <Button disabled={query.page + 1 >= result.data.totalPages} onClick={() => setQuery((value) => ({ ...value, page: value.page + 1 }))}>Next page</Button>
        </Stack>
      </>}
    </section>
  </Box>;
}
