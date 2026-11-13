import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { getDailyReport, getReportSummary } from '../../../api/adminReports';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import { tokens } from '../../../theme/theme';
import { formatDate } from '../../../utils/format';
import { defaultRange, validateRange } from './dateRange';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card };

/** Rates come from the server as percent with one decimal; this only fixes the displayed precision. */
const percent = (value) => `${Number(value ?? 0).toFixed(1)}%`;

function SummaryCard({ label, value }) {
  return (
    <Card elevation={0} sx={cardSx}>
      <CardContent>
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>{label}</Typography>
        <Typography variant="h5" sx={{ fontWeight: 700, color: tokens.palette.text }}>{value}</Typography>
      </CardContent>
    </Card>
  );
}

/**
 * FR-PAR-013: parcel report for an inclusive Asia/Colombo date range (default last 30 days, at most 366 days).
 * No charts (master 10). The summary and the daily table load, fail and retry independently.
 */
export default function AdminReportsPage() {
  const initial = defaultRange();
  const [draft, setDraft] = useState(initial);
  const [applied, setApplied] = useState(initial);
  const [rangeError, setRangeError] = useState(null);

  const summaryQuery = useQuery({
    queryKey: ['admin', 'reports', 'summary', applied],
    queryFn: () => getReportSummary(applied),
  });
  const dailyQuery = useQuery({
    queryKey: ['admin', 'reports', 'daily', applied],
    queryFn: () => getDailyReport(applied),
  });

  const apply = (event) => {
    event.preventDefault();
    const error = validateRange(draft.from, draft.to);
    setRangeError(error);
    if (!error) setApplied({ from: draft.from, to: draft.to });
  };

  const summary = summaryQuery.data;
  const rows = dailyQuery.data || [];

  return (
    <Box>
      <PageHeader title="Reports" subtitle="Parcel volumes and delivery outcomes by Asia/Colombo day." />

      <Card component="form" onSubmit={apply} aria-label="Report range" elevation={0} sx={{ ...cardSx, p: 2, mb: 3 }} noValidate>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr auto' }, alignItems: 'start' }}>
          <TextField label="From" type="date" value={draft.from} InputLabelProps={{ shrink: true }}
            onChange={(e) => setDraft((d) => ({ ...d, from: e.target.value }))} error={Boolean(rangeError)} />
          <TextField label="To" type="date" value={draft.to} InputLabelProps={{ shrink: true }}
            onChange={(e) => setDraft((d) => ({ ...d, to: e.target.value }))} error={Boolean(rangeError)}
            helperText={rangeError || 'Inclusive. At most 366 days.'} />
          <Button type="submit" variant="contained">Apply</Button>
        </Box>
      </Card>

      <Box component="section" aria-label="Summary" sx={{ mb: 3 }}>
        {summaryQuery.isPending && <LoadingState message="Loading summary..." minHeight={120} />}
        {summaryQuery.isError && (
          <ErrorState message={summaryQuery.error?.detail || 'Failed to load the summary.'}
            onRetry={() => summaryQuery.refetch()} minHeight={120} />
        )}
        {summary && (
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
            <SummaryCard label="Created" value={summary.created} />
            <SummaryCard label="Delivered" value={summary.delivered} />
            <SummaryCard label="Failed" value={summary.failed} />
            <SummaryCard label="Cancelled" value={summary.cancelled} />
            <SummaryCard label="In progress" value={summary.inProgress} />
            <SummaryCard label="Success rate" value={percent(summary.successRate)} />
            <SummaryCard label="Failure rate" value={percent(summary.failureRate)} />
          </Box>
        )}
      </Box>

      <Box component="section" aria-label="Daily breakdown">
        {dailyQuery.isPending && <LoadingState message="Loading daily breakdown..." />}
        {dailyQuery.isError && (
          <ErrorState message={dailyQuery.error?.detail || 'Failed to load the daily breakdown.'}
            onRetry={() => dailyQuery.refetch()} />
        )}
        {dailyQuery.isSuccess && rows.length === 0 && (
          <EmptyState title="No data" description="There are no days in this range." />
        )}
        {dailyQuery.isSuccess && rows.length > 0 && (
          <Card elevation={0} sx={cardSx}>
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Daily report">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell align="right">Created</TableCell>
                    <TableCell align="right">Delivered</TableCell>
                    <TableCell align="right">Failed</TableCell>
                    <TableCell align="right">Cancelled</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((row) => (
                    <TableRow key={row.date} hover>
                      <TableCell>{formatDate(row.date)}</TableCell>
                      <TableCell align="right">{row.created}</TableCell>
                      <TableCell align="right">{row.delivered}</TableCell>
                      <TableCell align="right">{row.failed}</TableCell>
                      <TableCell align="right">{row.cancelled}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Card>
        )}
      </Box>
    </Box>
  );
}
