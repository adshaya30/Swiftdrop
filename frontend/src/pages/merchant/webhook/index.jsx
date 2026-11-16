import React, { useState, useEffect, useCallback, useRef } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import Table from '@mui/material/Table';
import TableHead from '@mui/material/TableHead';
import TableBody from '@mui/material/TableBody';
import TableRow from '@mui/material/TableRow';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TablePagination from '@mui/material/TablePagination';
import Paper from '@mui/material/Paper';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogActions from '@mui/material/DialogActions';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Grid from '@mui/material/Grid';

import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import RefreshOutlinedIcon from '@mui/icons-material/RefreshOutlined';
import SendOutlinedIcon from '@mui/icons-material/SendOutlined';
import SyncOutlinedIcon from '@mui/icons-material/SyncOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import HourglassEmptyOutlinedIcon from '@mui/icons-material/HourglassEmptyOutlined';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';

import { tokens } from '../../../theme/theme';
import PageHeader from '../../../components/PageHeader';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import { ConfirmDialog } from '../../../components/ConfirmDialog';
import { formatDateTime } from '../../../utils/format';
import {
  getWebhook,
  saveWebhook,
  rotateSecret,
  sendTest,
  listDeliveries,
  retryDelivery,
  getWebhookSummary,
} from '../../../api/webhook';

const POLLING_INTERVAL_MS = 30000; // 30 seconds auto-refresh

/**
 * Chip for webhook delivery statuses.
 */
function WebhookStatusChip({ status }) {
  const norm = (status || '').toUpperCase();
  if (norm === 'SUCCEEDED') {
    return (
      <Chip
        size="small"
        icon={<CheckCircleOutlineIcon fontSize="inherit" />}
        label="Succeeded"
        sx={{
          backgroundColor: '#F0FDF4',
          color: '#16A34A',
          fontWeight: 600,
          border: '1px solid #BBF7D0',
          borderRadius: tokens.radius.chip,
        }}
      />
    );
  }
  if (norm === 'FAILED') {
    return (
      <Chip
        size="small"
        icon={<ErrorOutlineIcon fontSize="inherit" />}
        label="Failed"
        sx={{
          backgroundColor: '#FEF2F2',
          color: '#DC2626',
          fontWeight: 600,
          border: '1px solid #FECACA',
          borderRadius: tokens.radius.chip,
        }}
      />
    );
  }
  return (
    <Chip
      size="small"
      icon={<HourglassEmptyOutlinedIcon fontSize="inherit" />}
      label="Pending"
      sx={{
        backgroundColor: '#FFFBEB',
        color: '#D97706',
        fontWeight: 600,
        border: '1px solid #FDE68A',
        borderRadius: tokens.radius.chip,
      }}
    />
  );
}

export default function MerchantWebhookPage() {
  // Config state
  const [url, setUrl] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [hasSecret, setHasSecret] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [configSaving, setConfigSaving] = useState(false);
  const [configError, setConfigError] = useState(null);
  const [configSuccess, setConfigSuccess] = useState(null);

  // Secret display modal (shown strictly ONCE on first save or rotate)
  const [displayedSecret, setDisplayedSecret] = useState(null);
  const [copiedSecret, setCopiedSecret] = useState(false);

  // Dialog state for Rotate Secret
  const [rotateConfirmOpen, setRotateConfirmOpen] = useState(false);
  const [rotating, setRotating] = useState(false);

  // Test Ping state
  const [testingPing, setTestingPing] = useState(false);
  const [testResult, setTestResult] = useState(null);

  // Summary Counters state
  const [summary, setSummary] = useState({
    failed24h: '0',
    pending: '0',
    succeeded24h: '0',
  });
  const [summaryLoading, setSummaryLoading] = useState(false);

  // Delivery log table state
  const [deliveries, setDeliveries] = useState([]);
  const [deliveriesLoading, setDeliveriesLoading] = useState(false);
  const [deliveriesError, setDeliveriesError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalDeliveries, setTotalDeliveries] = useState(0);

  // Dialog state for manual delivery retry
  const [retryTarget, setRetryTarget] = useState(null);
  const [retrying, setRetrying] = useState(false);

  // Validation error state for URL
  const [urlValidationError, setUrlValidationError] = useState('');

  const pollingRef = useRef(null);

  // URL Validation (HTTPS or local demo, max 500 chars)
  const validateWebhookUrl = (value) => {
    if (!value || !value.trim()) {
      return 'Webhook URL is required';
    }
    if (value.length > 500) {
      return 'Webhook URL cannot exceed 500 characters';
    }
    const isHttps = /^https:\/\/[^/]+/i.test(value);
    const isLocalHttp = /^http:\/\/(localhost|127\.0\.0\.1|mock-merchant)(:\d+)?/i.test(value);
    if (!isHttps && !isLocalHttp) {
      return 'URL must use HTTPS protocol (or http:// for local mock merchant)';
    }
    try {
      new URL(value);
      return '';
    } catch {
      return 'Please enter a valid URL';
    }
  };

  // Fetch summary counters
  const fetchSummary = useCallback(async () => {
    setSummaryLoading(true);
    try {
      const data = await getWebhookSummary();
      setSummary({
        failed24h: String(data.failed24h ?? 0),
        pending: String(data.pending ?? 0),
        succeeded24h: String(data.succeeded24h ?? 0),
      });
    } catch (err) {
      // If notification-service fails to answer, show "unavailable" without breaking the page
      setSummary({
        failed24h: 'unavailable',
        pending: 'unavailable',
        succeeded24h: 'unavailable',
      });
    } finally {
      setSummaryLoading(false);
    }
  }, []);

  // Fetch delivery logs
  const fetchDeliveries = useCallback(async (isSilent = false) => {
    if (!isSilent) setDeliveriesLoading(true);
    setDeliveriesError(null);
    try {
      const params = {
        page,
        size: rowsPerPage,
      };
      if (statusFilter !== 'ALL') {
        params.status = statusFilter;
      }
      const res = await listDeliveries(params);
      if (res && res.content) {
        setDeliveries(res.content);
        setTotalDeliveries(res.totalElements ?? res.content.length);
      } else if (Array.isArray(res)) {
        setDeliveries(res);
        setTotalDeliveries(res.length);
      } else {
        setDeliveries([]);
        setTotalDeliveries(0);
      }
    } catch (err) {
      setDeliveriesError(err?.detail || err?.message || 'Failed to load delivery log');
    } finally {
      if (!isSilent) setDeliveriesLoading(false);
    }
  }, [page, rowsPerPage, statusFilter]);

  // Load initial webhook configuration
  const loadConfig = useCallback(async () => {
    setInitialLoading(true);
    setConfigError(null);
    try {
      const data = await getWebhook();
      if (data) {
        setUrl(data.url || '');
        setEnabled(data.enabled ?? true);
        setHasSecret(Boolean(data.hasSecret));
      }
    } catch (err) {
      setConfigError(err?.detail || err?.message || 'Failed to load webhook configuration');
    } finally {
      setInitialLoading(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    loadConfig();
    fetchSummary();
    fetchDeliveries();
  }, [loadConfig, fetchSummary, fetchDeliveries]);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    pollingRef.current = setInterval(() => {
      fetchDeliveries(true);
      fetchSummary();
    }, POLLING_INTERVAL_MS);

    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [fetchDeliveries, fetchSummary]);

  // Handle Save
  const handleSaveConfig = async (e) => {
    if (e) e.preventDefault();
    const errorMsg = validateWebhookUrl(url);
    if (errorMsg) {
      setUrlValidationError(errorMsg);
      return;
    }
    setUrlValidationError('');
    setConfigError(null);
    setConfigSuccess(null);
    setConfigSaving(true);

    try {
      const res = await saveWebhook({ url: url.trim(), enabled });
      setHasSecret(Boolean(res.hasSecret));
      setConfigSuccess('Webhook configuration saved successfully.');

      // If this was the first save, backend generated and returned the secret once
      if (res.secret) {
        setDisplayedSecret(res.secret);
      }
      fetchSummary();
    } catch (err) {
      // Do not clear the user's input URL on server error
      setConfigError(err?.detail || err?.message || 'Failed to save webhook settings');
    } finally {
      setConfigSaving(false);
    }
  };

  // Handle Rotate Secret
  const handleConfirmRotate = async () => {
    setRotating(true);
    setConfigError(null);
    try {
      const res = await rotateSecret();
      if (res.secret) {
        setDisplayedSecret(res.secret);
        setHasSecret(true);
        setConfigSuccess('Webhook secret rotated successfully.');
      }
    } catch (err) {
      setConfigError(err?.detail || err?.message || 'Failed to rotate webhook secret');
    } finally {
      setRotating(false);
      setRotateConfirmOpen(false);
    }
  };

  // Handle Send Test Ping
  const handleSendTest = async () => {
    setTestingPing(true);
    setTestResult(null);
    try {
      const res = await sendTest();
      setTestResult({
        type: 'success',
        message: res.message || 'Test webhook ping enqueued successfully. Check delivery log below.',
      });
      // Refresh deliveries and summary to show the newly enqueued ping
      fetchDeliveries();
      fetchSummary();
    } catch (err) {
      setTestResult({
        type: 'error',
        message: err?.detail || err?.message || 'Failed to send test ping',
      });
    } finally {
      setTestingPing(false);
    }
  };

  // Handle Retry Delivery
  const handleConfirmRetry = async () => {
    if (!retryTarget) return;
    setRetrying(true);
    try {
      await retryDelivery(retryTarget.eventId);
      setTestResult({
        type: 'success',
        message: `Delivery ${retryTarget.eventId} re-queued for immediate retry.`,
      });
      setRetryTarget(null);
      fetchDeliveries();
      fetchSummary();
    } catch (err) {
      setTestResult({
        type: 'error',
        message: err?.detail || err?.message || 'Failed to re-queue delivery',
      });
      setRetryTarget(null);
    } finally {
      setRetrying(false);
    }
  };

  const handleCopySecret = () => {
    if (displayedSecret) {
      navigator.clipboard.writeText(displayedSecret);
      setCopiedSecret(true);
      setTimeout(() => setCopiedSecret(false), 2000);
    }
  };

  const handleCloseSecretDialog = () => {
    // Clear secret immediately from state on close
    setDisplayedSecret(null);
    setCopiedSecret(false);
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto', p: { xs: 2, md: 3 } }}>
      <PageHeader
        title="Webhook Settings"
        subtitle="Configure real-time event notifications for your parcel lifecycle updates"
        action={
          <Button
            variant="outlined"
            startIcon={<RefreshOutlinedIcon />}
            onClick={() => {
              fetchSummary();
              fetchDeliveries();
            }}
            disabled={deliveriesLoading}
            sx={{ borderColor: tokens.palette.border, color: tokens.palette.text }}
          >
            Refresh
          </Button>
        }
      />

      {/* Top Summary Counters */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <Card
            sx={{
              borderRadius: tokens.radius.card,
              border: `1px solid ${tokens.palette.border}`,
              backgroundColor: tokens.palette.surface,
              boxShadow: 'none',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Typography variant="body2" sx={{ color: tokens.palette.mutedText, fontWeight: 500 }}>
                Pending Delivery
              </Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: tokens.palette.text, mt: 0.5 }}>
                {summaryLoading ? <CircularProgress size={24} /> : summary.pending}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card
            sx={{
              borderRadius: tokens.radius.card,
              border: `1px solid ${tokens.palette.border}`,
              backgroundColor: tokens.palette.surface,
              boxShadow: 'none',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Typography variant="body2" sx={{ color: tokens.palette.mutedText, fontWeight: 500 }}>
                Succeeded (Last 24h)
              </Typography>
              <Typography variant="h4" sx={{ fontWeight: 700, color: '#16A34A', mt: 0.5 }}>
                {summaryLoading ? <CircularProgress size={24} /> : summary.succeeded24h}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Card
            sx={{
              borderRadius: tokens.radius.card,
              border: `1px solid ${tokens.palette.border}`,
              backgroundColor: tokens.palette.surface,
              boxShadow: 'none',
            }}
          >
            <CardContent sx={{ p: 2.5 }}>
              <Typography variant="body2" sx={{ color: tokens.palette.mutedText, fontWeight: 500 }}>
                Failed (Last 24h)
              </Typography>
              <Typography
                variant="h4"
                sx={{
                  fontWeight: 700,
                  color: summary.failed24h === '0' ? tokens.palette.text : tokens.palette.destructive,
                  mt: 0.5,
                }}
              >
                {summaryLoading ? <CircularProgress size={24} /> : summary.failed24h}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Alerts */}
      {configSuccess && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setConfigSuccess(null)}>
          {configSuccess}
        </Alert>
      )}
      {configError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setConfigError(null)}>
          {configError}
        </Alert>
      )}
      {testResult && (
        <Alert severity={testResult.type} sx={{ mb: 2 }} onClose={() => setTestResult(null)}>
          {testResult.message}
        </Alert>
      )}

      {/* Webhook Configuration Card */}
      <Card
        sx={{
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          backgroundColor: tokens.palette.surface,
          boxShadow: 'none',
          mb: 4,
        }}
      >
        <CardContent sx={{ p: { xs: 2, md: 3 } }}>
          <Typography variant="h6" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 1 }}>
            Configuration & Secret
          </Typography>
          <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 3 }}>
            SwiftDrop delivers HMAC-SHA256 signed JSON payloads to your HTTPS endpoint when parcel statuses change.
          </Typography>

          {initialLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={32} />
            </Box>
          ) : (
            <Box component="form" onSubmit={handleSaveConfig} noValidate>
              <Stack spacing={3}>
                <TextField
                  fullWidth
                  id="webhook-url-input"
                  label="Webhook Target URL"
                  placeholder="https://api.yourdomain.com/webhooks/swiftdrop"
                  value={url}
                  onChange={(e) => {
                    setUrl(e.target.value);
                    if (urlValidationError) setUrlValidationError('');
                  }}
                  error={Boolean(urlValidationError)}
                  helperText={
                    urlValidationError ||
                    'Must be a valid HTTPS URL up to 500 characters (or http:// for local mock merchant).'
                  }
                  inputProps={{ maxLength: 500 }}
                  disabled={configSaving}
                />

                <Box
                  sx={{
                    display: 'flex',
                    flexDirection: { xs: 'column', sm: 'row' },
                    justifyContent: 'space-between',
                    alignItems: { xs: 'flex-start', sm: 'center' },
                    gap: 2,
                    pt: 1,
                  }}
                >
                  <FormControlLabel
                    control={
                      <Switch
                        checked={enabled}
                        onChange={(e) => setEnabled(e.target.checked)}
                        color="primary"
                        disabled={configSaving}
                      />
                    }
                    label={
                      <Box>
                        <Typography variant="body1" sx={{ fontWeight: 600 }}>
                          Deliveries Active
                        </Typography>
                        <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                          Enable or pause dispatching webhooks to this URL
                        </Typography>
                      </Box>
                    }
                  />

                  {/* Masked Secret Status */}
                  <Box
                    sx={{
                      p: 1.5,
                      borderRadius: tokens.radius.control,
                      backgroundColor: tokens.palette.mutedSurface,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1.5,
                    }}
                  >
                    <KeyOutlinedIcon sx={{ color: tokens.palette.mutedText, fontSize: 20 }} />
                    <Box>
                      <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                        Signing Secret
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: 'monospace' }}>
                        {hasSecret ? 'whsec_••••••••••••••••••••••••••••••••••••' : 'Not configured yet'}
                      </Typography>
                    </Box>
                  </Box>
                </Box>

                <Box
                  sx={{
                    display: 'flex',
                    flexWrap: 'wrap',
                    alignItems: 'center',
                    gap: 1.5,
                    pt: 1,
                  }}
                >
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={configSaving}
                    sx={{
                      backgroundColor: tokens.palette.primary,
                      '&:hover': { backgroundColor: tokens.palette.primaryHover },
                    }}
                  >
                    {configSaving ? 'Saving...' : 'Save Configuration'}
                  </Button>

                  <Button
                    variant="outlined"
                    startIcon={<SendOutlinedIcon />}
                    onClick={handleSendTest}
                    disabled={!hasSecret || !url || testingPing || configSaving}
                    sx={{
                      borderColor: tokens.palette.border,
                      color: tokens.palette.text,
                    }}
                  >
                    {testingPing ? 'Sending Ping...' : 'Send Test Ping'}
                  </Button>

                  {hasSecret && (
                    <Button
                      variant="outlined"
                      color="error"
                      startIcon={<SyncOutlinedIcon />}
                      onClick={() => setRotateConfirmOpen(true)}
                      disabled={configSaving || rotating}
                    >
                      Rotate Secret
                    </Button>
                  )}
                </Box>
              </Stack>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Webhook Delivery Log Section */}
      <Card
        sx={{
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          backgroundColor: tokens.palette.surface,
          boxShadow: 'none',
        }}
      >
        <CardContent sx={{ p: { xs: 2, md: 3 } }}>
          <Box
            sx={{
              display: 'flex',
              flexDirection: { xs: 'column', sm: 'row' },
              justifyContent: 'space-between',
              alignItems: { xs: 'flex-start', sm: 'center' },
              gap: 2,
              mb: 3,
            }}
          >
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700, color: tokens.palette.text }}>
                Delivery Log
              </Typography>
              <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                Audit history of all webhook dispatch attempts and retries (auto-refreshes every 30s)
              </Typography>
            </Box>

            {/* Filter by Status */}
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <InputLabel id="status-filter-label">Filter Status</InputLabel>
              <Select
                labelId="status-filter-label"
                id="status-filter-select"
                value={statusFilter}
                label="Filter Status"
                onChange={(e) => {
                  setStatusFilter(e.target.value);
                  setPage(0);
                }}
              >
                <MenuItem value="ALL">All Statuses</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="SUCCEEDED">Succeeded</MenuItem>
                <MenuItem value="FAILED">Failed</MenuItem>
              </Select>
            </FormControl>
          </Box>

          {deliveriesLoading && deliveries.length === 0 ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress size={36} />
            </Box>
          ) : deliveriesError ? (
            <ErrorState
              title="Unable to load deliveries"
              message={deliveriesError}
              onRetry={() => fetchDeliveries()}
            />
          ) : deliveries.length === 0 ? (
            <EmptyState
              title="No Webhook Deliveries"
              description="Deliveries will appear here when parcel status events occur or when you send a test ping."
              actionLabel="Send Test Ping"
              onAction={handleSendTest}
            />
          ) : (
            <>
              <TableContainer component={Paper} elevation={0} sx={{ border: `1px solid ${tokens.palette.border}` }}>
                <Table sx={{ minWidth: 700 }} aria-label="Webhook Deliveries">
                  <TableHead sx={{ backgroundColor: tokens.palette.mutedSurface }}>
                    <TableRow>
                      <TableCell sx={{ fontWeight: 600 }}>Event ID / Type</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Tracking #</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Status</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Attempts</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Last HTTP</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Error Summary</TableCell>
                      <TableCell sx={{ fontWeight: 600 }}>Time (Asia/Colombo)</TableCell>
                      <TableCell sx={{ fontWeight: 600, textAlign: 'right' }}>Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {deliveries.map((row) => (
                      <TableRow key={row.eventId} hover>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontWeight: 600 }}>
                            {row.eventId?.substring(0, 8)}...
                          </Typography>
                          <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                            {row.eventType}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {row.trackingNumber || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <WebhookStatusChip status={row.status} />
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {row.attemptCount} / 6
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                            {row.lastHttpStatus ?? '—'}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 220 }}>
                          <Typography
                            variant="body2"
                            sx={{
                              color: row.lastError ? tokens.palette.destructive : tokens.palette.mutedText,
                              fontSize: '0.8125rem',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                            title={row.lastError || ''}
                          >
                            {row.lastError || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontSize: '0.8125rem' }}>
                            {formatDateTime(row.lastAttemptAt || row.createdAt)}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ textAlign: 'right' }}>
                          {row.status === 'FAILED' && (
                            <Button
                              size="small"
                              variant="outlined"
                              onClick={() => setRetryTarget(row)}
                              sx={{
                                color: tokens.palette.primary,
                                borderColor: tokens.palette.border,
                                textTransform: 'none',
                                fontWeight: 600,
                              }}
                            >
                              Retry
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              <TablePagination
                rowsPerPageOptions={[5, 10, 25, 50]}
                component="div"
                count={totalDeliveries}
                rowsPerPage={rowsPerPage}
                page={page}
                onPageChange={handleChangePage}
                onRowsPerPageChange={handleChangeRowsPerPage}
              />
            </>
          )}
        </CardContent>
      </Card>

      {/* Secret Display Dialog (Shown strictly once upon first save or rotate) */}
      <Dialog
        open={Boolean(displayedSecret)}
        onClose={handleCloseSecretDialog}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: { borderRadius: tokens.radius.card, p: 1 },
        }}
      >
        <DialogTitle sx={{ fontWeight: 700, color: tokens.palette.text, display: 'flex', alignItems: 'center', gap: 1 }}>
          <KeyOutlinedIcon sx={{ color: tokens.palette.primary }} />
          Webhook Signing Secret
        </DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            <strong>Copy this secret now!</strong> For your security, this secret is encrypted at rest and will{' '}
            <strong>never</strong> be displayed again.
          </Alert>
          <DialogContentText sx={{ color: tokens.palette.mutedText, mb: 2 }}>
            Use this secret on your receiving server to compute and verify the HMAC-SHA256 signature in the{' '}
            <code>X-SwiftDrop-Signature</code> header.
          </DialogContentText>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              p: 2,
              borderRadius: tokens.radius.control,
              backgroundColor: tokens.palette.mutedSurface,
              border: `1px solid ${tokens.palette.border}`,
            }}
          >
            <Typography
              variant="body2"
              sx={{
                fontFamily: 'monospace',
                fontWeight: 600,
                wordBreak: 'break-all',
                color: tokens.palette.text,
              }}
            >
              {displayedSecret}
            </Typography>
            <Tooltip title={copiedSecret ? 'Copied!' : 'Copy Secret'}>
              <IconButton onClick={handleCopySecret} color="primary" sx={{ ml: 1 }}>
                <ContentCopyOutlinedIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button
            onClick={handleCloseSecretDialog}
            variant="contained"
            sx={{
              backgroundColor: tokens.palette.primary,
              '&:hover': { backgroundColor: tokens.palette.primaryHover },
            }}
          >
            I have securely saved this secret
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirm Rotate Secret Dialog */}
      <ConfirmDialog
        open={rotateConfirmOpen}
        title="Rotate Webhook Secret"
        message="Are you sure you want to rotate your webhook secret? Any existing integration verifying payloads with the current secret will immediately fail until updated with the new secret."
        confirmText={rotating ? 'Rotating...' : 'Rotate Secret'}
        cancelText="Cancel"
        isDestructive={true}
        loading={rotating}
        onConfirm={handleConfirmRotate}
        onCancel={() => setRotateConfirmOpen(false)}
      />

      {/* Confirm Retry Delivery Dialog */}
      <ConfirmDialog
        open={Boolean(retryTarget)}
        title="Retry Webhook Delivery"
        message={`Are you sure you want to retry delivery for event ${retryTarget?.eventId}? It will be re-queued immediately with attempt counter reset.`}
        confirmText={retrying ? 'Re-queueing...' : 'Retry Now'}
        cancelText="Cancel"
        loading={retrying}
        onConfirm={handleConfirmRetry}
        onCancel={() => setRetryTarget(null)}
      />
    </Box>
  );
}

