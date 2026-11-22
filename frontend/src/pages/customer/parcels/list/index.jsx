import React, { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Alert } from '@mui/material';
import { ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import EmptyState from '../../../../components/EmptyState';
import { tokens } from '../../../../theme/theme';
import { listParcels } from '../../../../api/customerParcels';
import ParcelCards from '../../components/ParcelCards';
import CustomerRequestError from '../../components/CustomerRequestError';
import { useCustomerRequest } from '../../components/useCustomerRequest';
import './list.css';

const defaults = { q: '', status: '', from: '', to: '', sortField: 'createdAt', direction: 'desc', size: '20' };
const defaultQuery = { page: 0, size: 20, sort: 'createdAt,desc' };

function ParcelPagination({ page, totalPages, onPageChange }) {
  const current = page + 1;
  const windowStart = Math.max(1, Math.min(current - 2, totalPages - 4));
  const windowEnd = Math.min(totalPages, windowStart + 4);
  const pages = [];
  if (windowStart > 1) pages.push(1);
  if (windowStart > 2) pages.push('before');
  for (let n = windowStart; n <= windowEnd; n += 1) pages.push(n);
  if (windowEnd < totalPages - 1 && totalPages > 0) pages.push('after');
  if (windowEnd < totalPages) pages.push(totalPages);

  return <nav className="customer-list-pagination" aria-label="Parcel pagination">
    <button type="button" className="customer-list-button" aria-label="Previous page" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
      <ChevronLeft size={17} aria-hidden="true" /><span>Previous</span>
    </button>
    <div className="customer-list-page-numbers">
      {pages.map((number) => typeof number === 'number'
        ? <button key={number} type="button" className="customer-list-button" aria-label={`Page ${number}`}
            aria-current={number === current ? 'page' : undefined} onClick={() => number !== current && onPageChange(number - 1)}>{number}</button>
        : <span key={number} aria-hidden="true">…</span>)}
    </div>
    <button type="button" className="customer-list-button" aria-label="Next page" disabled={current >= totalPages} onClick={() => onPageChange(page + 1)}>
      <span>Next</span><ChevronRight size={17} aria-hidden="true" />
    </button>
  </nav>;
}

function FilterField({ name, label, children }) {
  return <div className="customer-list-field">
    <label htmlFor={`parcel-filter-${name}`}>{label}</label>
    {children}
  </div>;
}

export default function CustomerParcelsPage() {
  const { key: navigationKey } = useLocation();
  const [draft, setDraft] = useState(defaults);
  const [query, setQuery] = useState(defaultQuery);
  const [revision, setRevision] = useState(0);
  const [validation, setValidation] = useState('');
  const resultsHeading = useRef(null);
  useLayoutEffect(() => {
    if (window.scrollY > 0) window.scrollTo(0, 0);
  }, [navigationKey]);
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
  const reset = () => {
    setDraft(defaults);
    setValidation('');
    setQuery(defaultQuery);
    setRevision((value) => value + 1);
  };
  const control = (field) => ({ id: `parcel-filter-${field}`, value: draft[field], onChange: update(field) });
  const select = (label, field, children) => <FilterField name={field} label={label}>
    <select {...control(field)}>{children}</select>
  </FilterField>;
  const date = (label, field) => <FilterField name={field} label={label}>
    <input {...control(field)} type="date" aria-invalid={Boolean(validation)}
      aria-describedby={validation ? 'parcel-filter-validation' : undefined} />
  </FilterField>;
  const changePage = (page) => {
    resultsHeading.current?.focus();
    setQuery((value) => ({ ...value, page }));
  };

  return <div className="customer-page customer-parcel-list">
    <header className="customer-list-header">
      <h1>My parcels</h1>
    </header>
    <form onSubmit={apply} className="customer-list-filters" aria-label="Parcel filters">
      <div className="customer-list-filter-grid">
        <FilterField name="q" label="Tracking-number prefix">
          <input {...control('q')} type="text" maxLength={13} />
        </FilterField>
        {select('Status', 'status', <><option value="">All statuses</option>{Object.entries(tokens.status).map(([status, config]) => <option key={status} value={status}>{config.label}</option>)}</>)}
        {date('From date', 'from')}
        {date('To date', 'to')}
        {select('Sort by', 'sortField', <><option value="createdAt">Creation date</option><option value="status">Status</option><option value="trackingNumber">Tracking number</option></>)}
        {select('Sort direction', 'direction', <><option value="desc">Descending</option><option value="asc">Ascending</option></>)}
        {select('Parcels per page', 'size', [20, 50, 100].map((size) => <option key={size} value={size}>{size}</option>))}
        <button className="customer-list-button" type="button" onClick={reset}>Reset filters</button>
        <button className="customer-list-button customer-list-primary" type="submit">Apply filters</button>
        <Link to="/deliveries/new" className="customer-list-button customer-list-primary"><Plus size={18} aria-hidden="true" />New Delivery</Link>
      </div>
      {validation && <Alert id="parcel-filter-validation" severity="error" role="alert">{validation}</Alert>}
    </form>
    <section className="customer-list-results" aria-labelledby="parcel-results-heading" aria-busy={result.status === 'loading'}>
      <div className="customer-list-results-header">
        <h2 id="parcel-results-heading" tabIndex={-1} ref={resultsHeading}>Parcel results</h2>
        {result.status === 'success' && <p role="status">{result.data.totalItems} {result.data.totalItems === 1 ? 'parcel' : 'parcels'} <span aria-hidden="true">·</span> Page {result.data.totalPages ? query.page + 1 : 0} of {result.data.totalPages}</p>}
      </div>
      {result.status === 'loading' && <div className="customer-list-loading">
        <p>Loading your parcels…</p>
        <div className="customer-list-placeholder" aria-hidden="true"><span /><span /><span /></div>
      </div>}
      {result.status === 'error' && <CustomerRequestError error={result.error} onRetry={() => setRevision((value) => value + 1)} />}
      {result.status === 'success' && <>
        {result.data.items.length ? <ParcelCards items={result.data.items} detailsBase="/deliveries" showDetails /> : <EmptyState title="No parcels found" description="Try different filters or create a new delivery." minHeight={160} />}
        <ParcelPagination page={query.page} totalPages={result.data.totalPages} onPageChange={changePage} />
      </>}
    </section>
  </div>;
}
