import React, { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import StorefrontOutlinedIcon from '@mui/icons-material/StorefrontOutlined';
import { tokens } from '../../../theme/theme';
import PageHeader from '../../../components/PageHeader';
import LoadingState from '../../../components/LoadingState';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import { formatDate } from '../../../utils/format';
import { listMerchants } from '../../../api/adminMerchants';
import { MERCHANT_STATUSES, MERCHANT_STATUS_LABELS, MerchantStatusChip } from './MerchantStatusChip';
import { MERCHANTS_QUERY_KEY } from './queryKeys';

/**
 * Merchant directory for ADMIN users. SRS v2.1 - FR-AUTH-017, UC-A05.
 * Filters (status, business-name prefix) and paging live in the URL, so a link such as
 * /admin/merchants?status=PENDING (the dashboard card) opens the right view and the browser back button works.
 * `lockedStatus` fixes the status filter (used by the pending-merchants page).
 */

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_SIZE = 20;
const SEARCH_DELAY_MS = 400;

const headCellSx = { fontWeight: 700, color: tokens.palette.text, whiteSpace: 'nowrap' };
const controlSx = { '& .MuiOutlinedInput-root': { minHeight: tokens.controlHeight.standard } };

const formatPercent = (value) => `${Number(value ?? 0).toFixed(2)}%`;

export default function MerchantDirectory({ lockedStatus = null, title = 'Merchants', subtitle }) {
  const [searchParams, setSearchParams] = useSearchParams();

  const statusParam = (searchParams.get('status') || '').toUpperCase();
  const status = lockedStatus || (MERCHANT_STATUSES.includes(statusParam) ? statusParam : '');
  const q = searchParams.get('q') || '';
  const page = Math.max(0, parseInt(searchParams.get('page') || '0', 10) || 0);
  const requestedSize = parseInt(searchParams.get('size') || '', 10);
  const size = PAGE_SIZES.includes(requestedSize) ? requestedSize : DEFAULT_SIZE;

  const [searchText, setSearchText] = useState(q);

  const updateParams = useCallback(
    (changes) => {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          Object.entries(changes).forEach(([key, value]) => {
            if (value === '' || value === null || value === undefined || (key === 'page' && value === 0)) {
              next.delete(key);
            } else {
              next.set(key, String(value));
            }
          });
          return next;
        },
        { replace: true }
      );
    },
    [setSearchParams]
  );

  // The search box updates the URL (and so the request) only after the user stops typing.
  useEffect(() => {
    const timer = setTimeout(() => {
      const trimmed = searchText.trim();
      if (trimmed !== q) {
        updateParams({ q: trimmed, page: 0 });
      }
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [searchText, q, updateParams]);

  const query = useQuery({
    queryKey: [...MERCHANTS_QUERY_KEY, { status, q, page, size }],
    queryFn: () => listMerchants({ status: status || undefined, q: q || undefined, page, size }),
    placeholderData: keepPreviousData,
  });

  const items = query.data?.items ?? [];
  const totalItems = query.data?.totalItems ?? 0;
  const filtered = Boolean(q || (status && !lockedStatus));

  const clearFilters = () => {
    setSearchText('');
    updateParams({ q: '', status: lockedStatus ? undefined : '', page: 0 });
  };

  let content;
  if (query.isPending) {
    content = <LoadingState message="Loading merchants..." />;
  } else if (query.isError) {
    content = (
      <ErrorState
        title="Could not load merchants"
        message={query.error?.detail || 'Please try again.'}
        onRetry={() => query.refetch()}
      />
    );
  } else if (items.length === 0) {
    content = filtered ? (
      <EmptyState
        icon={StorefrontOutlinedIcon}
        title="No merchants match your filters"
        description="Try a different name or status."
        actionLabel="Clear filters"
        onAction={clearFilters}
      />
    ) : (
      <EmptyState
        icon={StorefrontOutlinedIcon}
        title={lockedStatus === 'PENDING' ? 'No merchants are waiting for approval' : 'No merchants yet'}
        description={
          lockedStatus === 'PENDING'
            ? 'New registrations will appear here.'
            : 'Merchants appear here after they register.'
        }
      />
    );
  } else {
    content = (
      <Paper variant="outlined" sx={{ borderRadius: tokens.radius.card, borderColor: tokens.palette.border }}>
        <TableContainer>
          <Table aria-label="Merchants">
            <TableHead>
              <TableRow>
                <TableCell sx={headCellSx}>Business</TableCell>
                <TableCell sx={headCellSx}>City</TableCell>
                <TableCell sx={headCellSx}>Status</TableCell>
                <TableCell sx={headCellSx} align="right">
                  Discount
                </TableCell>
                <TableCell sx={headCellSx}>Registered</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((merchant) => (
                <TableRow key={merchant.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>
                    <Link
                      component={RouterLink}
                      to={`/admin/merchants/${merchant.id}`}
                      underline="hover"
                      sx={{ color: tokens.palette.text, fontWeight: 600 }}
                    >
                      {merchant.businessName}
                    </Link>
                  </TableCell>
                  <TableCell>{merchant.city || '—'}</TableCell>
                  <TableCell>
                    <MerchantStatusChip status={merchant.status} />
                  </TableCell>
                  <TableCell align="right">{formatPercent(merchant.discountPercent)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDate(merchant.createdAt)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={totalItems}
          page={page}
          rowsPerPage={size}
          rowsPerPageOptions={PAGE_SIZES}
          onPageChange={(_event, next) => updateParams({ page: next })}
          onRowsPerPageChange={(event) => updateParams({ size: event.target.value, page: 0 })}
        />
      </Paper>
    );
  }

  return (
    <Box>
      <PageHeader
        title={title}
        subtitle={subtitle || 'Review registrations, approve or reject merchants, and manage their discount.'}
      />

      <Box
        role="search"
        aria-label="Merchant filters"
        sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: lockedStatus ? '1fr' : '220px 1fr' }, gap: 2, mb: 2 }}
      >
        {!lockedStatus && (
          <TextField
            select
            id="merchant-status-filter"
            label="Status"
            size="small"
            value={status}
            onChange={(event) => updateParams({ status: event.target.value, page: 0 })}
            sx={controlSx}
            slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
          >
            <MenuItem value="">All statuses</MenuItem>
            {MERCHANT_STATUSES.map((value) => (
              <MenuItem key={value} value={value}>
                {MERCHANT_STATUS_LABELS[value]}
              </MenuItem>
            ))}
          </TextField>
        )}
        <TextField
          id="merchant-search"
          type="search"
          label="Search by business name"
          size="small"
          value={searchText}
          onChange={(event) => setSearchText(event.target.value)}
          placeholder="Starts with..."
          sx={controlSx}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Box>

      <Typography role="status" aria-live="polite" variant="body2" sx={{ color: tokens.palette.mutedText, mb: 1, minHeight: 20 }}>
        {query.isSuccess ? `${totalItems} ${totalItems === 1 ? 'merchant' : 'merchants'}${filtered ? ' match your filters' : ''}` : ''}
      </Typography>

      {content}
    </Box>
  );
}
