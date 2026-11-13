import React, { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { listAuditLogs } from '../../../api/auditLogs';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import { tokens } from '../../../theme/theme';
import { formatDateTime } from '../../../utils/format';

const EMPTY_FILTERS = { action: '', actorId: '', from: '', to: '' };
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** FR-TRK-005: details are shown as read-only text. React escapes the text; nothing is rendered as HTML. */
function Details({ value }) {
  if (value === null || value === undefined) return '—';
  return (
    <Box
      component="pre"
      tabIndex={0}
      aria-label="Details"
      sx={{
        m: 0, p: 1, maxHeight: 120, maxWidth: 360, overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-word',
        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace', fontSize: '0.75rem',
        backgroundColor: tokens.palette.mutedSurface, borderRadius: '8px', color: tokens.palette.text,
      }}
    >
      {JSON.stringify(value, null, 2)}
    </Box>
  );
}

/**
 * FR-TRK-005, NFR-SEC-019: the audit log, newest first, filtered by action, actor and Asia/Colombo dates. Entries never
 * hold passwords or contact data (BR-28); the page shows what the server returns, read-only.
 */
export default function AdminAuditLogsPage() {
  const [draft, setDraft] = useState(EMPTY_FILTERS);
  const [applied, setApplied] = useState(EMPTY_FILTERS);
  const [errors, setErrors] = useState({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);

  const logsQuery = useQuery({
    queryKey: ['admin', 'audit-logs', applied, page, size],
    queryFn: () => listAuditLogs({ ...applied, page, size }),
    placeholderData: keepPreviousData,
  });

  const setField = (name) => (event) => setDraft((d) => ({ ...d, [name]: event.target.value }));

  const apply = (event) => {
    event.preventDefault();
    const next = {};
    const actorId = draft.actorId.trim();
    if (actorId && !UUID.test(actorId)) next.actorId = 'Enter a valid actor id (UUID)';
    if (draft.from && draft.to && draft.from > draft.to) next.to = 'The "from" date must not be after the "to" date.';
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    setApplied({ ...draft, action: draft.action.trim(), actorId });
    setPage(0);
  };

  const clear = () => {
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setErrors({});
    setPage(0);
  };

  const items = logsQuery.data?.items || [];

  return (
    <Box>
      <PageHeader title="Audit log" subtitle="Privileged actions across all services, newest first. Entries are kept for one year." />

      <Card component="form" onSubmit={apply} aria-label="Audit log filters" elevation={0} noValidate
        sx={{ p: 2, mb: 3, border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card }}>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', lg: 'repeat(4, 1fr)' } }}>
          <TextField label="Action" value={draft.action} onChange={setField('action')}
            inputProps={{ maxLength: 60 }} helperText="Exact name, for example PRICING_UPDATED" />
          <TextField label="Actor id" value={draft.actorId} onChange={setField('actorId')}
            inputProps={{ maxLength: 36 }} error={Boolean(errors.actorId)} helperText={errors.actorId || ' '} />
          <TextField label="From" type="date" value={draft.from} onChange={setField('from')}
            InputLabelProps={{ shrink: true }} />
          <TextField label="To" type="date" value={draft.to} onChange={setField('to')}
            InputLabelProps={{ shrink: true }} error={Boolean(errors.to)} helperText={errors.to || ' '} />
        </Box>
        <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
          <Button type="submit" variant="contained">Apply filters</Button>
          <Button type="button" onClick={clear}>Clear</Button>
        </Box>
      </Card>

      {logsQuery.isPending && <LoadingState message="Loading audit log..." />}
      {logsQuery.isError && (
        <ErrorState message={logsQuery.error?.detail || 'Failed to load the audit log.'}
          onRetry={() => logsQuery.refetch()} />
      )}
      {logsQuery.isSuccess && items.length === 0 && (
        <EmptyState title="No audit entries" description="No entry matches the current filters." />
      )}

      {logsQuery.isSuccess && items.length > 0 && (
        <Card elevation={0} sx={{ border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card }}>
          <TableContainer sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="Audit entries">
              <TableHead>
                <TableRow>
                  <TableCell>When</TableCell>
                  <TableCell>Action</TableCell>
                  <TableCell>Actor</TableCell>
                  <TableCell>Service</TableCell>
                  <TableCell>Entity</TableCell>
                  <TableCell>Details</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {items.map((entry) => (
                  <TableRow key={entry.id} hover sx={{ verticalAlign: 'top' }}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(entry.occurredAt)}</TableCell>
                    <TableCell sx={{ fontWeight: 600 }}>{entry.action}</TableCell>
                    <TableCell>
                      <Typography variant="body2">{entry.actorRole}</Typography>
                      <Typography variant="caption" sx={{ color: tokens.palette.mutedText, wordBreak: 'break-all' }}>
                        {entry.actorUserId || 'System'}
                      </Typography>
                    </TableCell>
                    <TableCell>{entry.service}</TableCell>
                    <TableCell>
                      <Typography variant="body2">{entry.entityType}</Typography>
                      <Typography variant="caption" sx={{ color: tokens.palette.mutedText, wordBreak: 'break-all' }}>
                        {entry.entityId}
                      </Typography>
                    </TableCell>
                    <TableCell><Details value={entry.details} /></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={logsQuery.data?.totalItems || 0}
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
