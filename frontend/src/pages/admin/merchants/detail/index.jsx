import React, { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import ArrowBackOutlinedIcon from '@mui/icons-material/ArrowBackOutlined';
import { tokens } from '../../../../theme/theme';
import PageHeader from '../../../../components/PageHeader';
import LoadingState from '../../../../components/LoadingState';
import ErrorState from '../../../../components/ErrorState';
import { ConfirmDialog } from '../../../../components/ConfirmDialog';
import { formatDateTime } from '../../../../utils/format';
import {
  approveMerchant,
  getMerchant,
  reinstateMerchant,
  rejectMerchant,
  setDiscount,
  suspendMerchant,
} from '../../../../api/adminMerchants';
import { MerchantStatusChip } from '../MerchantStatusChip';
import ReasonDialog from '../ReasonDialog';
import DiscountDialog from '../DiscountDialog';
import { MERCHANTS_QUERY_KEY, merchantDetailKey } from '../queryKeys';

/**
 * Merchant review page (route /admin/merchants/:id). SRS v2.1 - FR-AUTH-017, UC-A05, BR-12.
 * Status actions follow the lifecycle of BR-12: PENDING -> approve or reject; REJECTED -> approve; APPROVED -> suspend;
 * SUSPENDED -> reinstate. Reject and suspend need a reason (10-300 characters). The server enforces all of it; when its
 * answer is INVALID_STATUS_TRANSITION (another admin acted first) the page reloads the merchant.
 */

const ACTIONS_BY_STATUS = {
  PENDING: ['approve', 'reject'],
  REJECTED: ['approve'],
  APPROVED: ['suspend'],
  SUSPENDED: ['reinstate'],
};

const STATUS_HINT = {
  PENDING: 'This registration is waiting for your decision.',
  APPROVED: 'This merchant can log in, create API credentials and send orders.',
  REJECTED: 'This registration was rejected. The merchant cannot log in. You can still approve it.',
  SUSPENDED: 'This merchant is suspended: no login, no API tokens and no new orders.',
};

const SUCCESS_TEXT = {
  approve: 'Merchant approved. They can now log in and use the API.',
  reject: 'Merchant rejected.',
  suspend: 'Merchant suspended.',
  reinstate: 'Merchant reinstated.',
};

const STALE_TEXT = "This merchant's status changed in the meantime. The page has been refreshed, please check it and try again.";

const primarySx = {
  backgroundColor: tokens.palette.primary,
  minHeight: tokens.controlHeight.standard,
  fontWeight: 600,
  '&:hover': { backgroundColor: tokens.palette.primaryHover },
};
const dangerSx = {
  minHeight: tokens.controlHeight.standard,
  fontWeight: 600,
  color: tokens.palette.destructive,
  borderColor: tokens.palette.destructive,
  '&:hover': { borderColor: tokens.palette.destructiveHover, backgroundColor: tokens.status.FAILED.bg },
};

function Detail({ label, children }) {
  return (
    <Box>
      <Typography component="dt" variant="body2" sx={{ color: tokens.palette.mutedText, fontWeight: 600 }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, color: tokens.palette.text, wordBreak: 'break-word' }}>
        {children}
      </Typography>
    </Box>
  );
}

const orDash = (value) => (value === null || value === undefined || value === '' ? '—' : value);

export default function MerchantDetailPage() {
  const { id } = useParams();
  const queryClient = useQueryClient();
  const [dialog, setDialog] = useState(null); // approve | reject | suspend | reinstate | discount
  const [notice, setNotice] = useState(null); // { severity, text }

  const query = useQuery({
    queryKey: merchantDetailKey(id),
    queryFn: () => getMerchant(id),
    retry: (count, err) => err?.status !== 404 && count < 1,
  });
  const merchant = query.data;

  const applyUpdate = (updated, text) => {
    queryClient.setQueryData(merchantDetailKey(id), updated);
    queryClient.invalidateQueries({ queryKey: MERCHANTS_QUERY_KEY }); // directory and the dashboard counter
    setDialog(null);
    setNotice({ severity: 'success', text });
  };

  const handleStale = () => {
    setDialog(null);
    setNotice({ severity: 'warning', text: STALE_TEXT });
    queryClient.invalidateQueries({ queryKey: MERCHANTS_QUERY_KEY });
  };

  const simpleAction = useMutation({
    mutationFn: (type) => (type === 'approve' ? approveMerchant(id) : reinstateMerchant(id)),
    onSuccess: (updated, type) => applyUpdate(updated, SUCCESS_TEXT[type]),
    onError: (err) => {
      if (err?.code === 'INVALID_STATUS_TRANSITION') {
        handleStale();
      } else {
        setDialog(null);
        setNotice({ severity: 'error', text: err?.detail || 'The action could not be completed. Please try again.' });
      }
    },
  });

  const submitReason = (type) => async (reason) => {
    const updated = type === 'reject' ? await rejectMerchant(id, reason) : await suspendMerchant(id, reason);
    applyUpdate(updated, SUCCESS_TEXT[type]);
  };

  const submitDiscount = async (percent) => {
    const updated = await setDiscount(id, percent);
    applyUpdate(updated, `Discount updated to ${Number(updated?.discountPercent ?? percent).toFixed(2)}%.`);
  };

  const backLink = (
    <Link
      component={RouterLink}
      to="/admin/merchants"
      underline="hover"
      sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, mb: 2, color: tokens.palette.mutedText, fontWeight: 600 }}
    >
      <ArrowBackOutlinedIcon sx={{ fontSize: '1.1rem' }} aria-hidden="true" />
      All merchants
    </Link>
  );

  if (query.isPending) {
    return (
      <Box>
        {backLink}
        <LoadingState message="Loading merchant..." />
      </Box>
    );
  }

  if (query.isError) {
    const missing = query.error?.status === 404;
    return (
      <Box>
        {backLink}
        <ErrorState
          title={missing ? 'Merchant not found' : 'Could not load this merchant'}
          message={missing ? 'This merchant does not exist or was removed.' : query.error?.detail || 'Please try again.'}
          onRetry={missing ? undefined : () => query.refetch()}
        />
      </Box>
    );
  }

  const actions = ACTIONS_BY_STATUS[merchant.status] || [];
  const busy = simpleAction.isPending;

  return (
    <Box>
      {backLink}
      <PageHeader
        title={merchant.businessName}
        subtitle={STATUS_HINT[merchant.status]}
        action={<MerchantStatusChip status={merchant.status} size="medium" />}
      />

      {notice && (
        <Alert
          severity={notice.severity}
          role={notice.severity === 'error' ? 'alert' : 'status'}
          onClose={() => setNotice(null)}
          sx={{ mb: 2, borderRadius: tokens.radius.control }}
        >
          {notice.text}
        </Alert>
      )}

      <Card
        elevation={0}
        component="section"
        aria-labelledby="merchant-details-title"
        sx={{ p: { xs: 2, sm: 3 }, mb: 3, border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card }}
      >
        <Typography id="merchant-details-title" variant="h6" component="h2" sx={{ fontWeight: 700, mb: 2 }}>
          Merchant details
        </Typography>
        <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, m: 0 }}>
          <Detail label="Business name">{merchant.businessName}</Detail>
          <Detail label="Registration number">{orDash(merchant.businessRegistrationNo)}</Detail>
          <Detail label="Contact person">{orDash(merchant.contactName)}</Detail>
          <Detail label="Email">{orDash(merchant.email)}</Detail>
          <Detail label="Phone">{orDash(merchant.phone)}</Detail>
          <Detail label="Address">{[merchant.addressLine1, merchant.city].filter(Boolean).join(', ') || '—'}</Detail>
          <Detail label="Status">
            <MerchantStatusChip status={merchant.status} />
          </Detail>
          <Detail label="Reason">{orDash(merchant.statusReason)}</Detail>
          <Detail label="Discount">{`${Number(merchant.discountPercent ?? 0).toFixed(2)}%`}</Detail>
          <Detail label="Registered">{formatDateTime(merchant.createdAt)}</Detail>
          <Detail label="Last decision">{merchant.decidedAt ? formatDateTime(merchant.decidedAt) : '—'}</Detail>
          <Detail label="Decided by (admin ID)">{orDash(merchant.decidedBy)}</Detail>
        </Box>
      </Card>

      <Card
        elevation={0}
        component="section"
        aria-labelledby="merchant-actions-title"
        sx={{ p: { xs: 2, sm: 3 }, border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card }}
      >
        <Typography id="merchant-actions-title" variant="h6" component="h2" sx={{ fontWeight: 700, mb: 2 }}>
          Actions
        </Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5 }}>
          {actions.includes('approve') && (
            <Button variant="contained" disabled={busy} onClick={() => setDialog('approve')} sx={primarySx}>
              Approve
            </Button>
          )}
          {actions.includes('reinstate') && (
            <Button variant="contained" disabled={busy} onClick={() => setDialog('reinstate')} sx={primarySx}>
              Reinstate
            </Button>
          )}
          {actions.includes('reject') && (
            <Button variant="outlined" disabled={busy} onClick={() => setDialog('reject')} sx={dangerSx}>
              Reject
            </Button>
          )}
          {actions.includes('suspend') && (
            <Button variant="outlined" disabled={busy} onClick={() => setDialog('suspend')} sx={dangerSx}>
              Suspend
            </Button>
          )}
          <Button variant="outlined" disabled={busy} onClick={() => setDialog('discount')} sx={{ minHeight: tokens.controlHeight.standard, fontWeight: 600 }}>
            Edit discount
          </Button>
        </Box>
      </Card>

      <ConfirmDialog
        open={dialog === 'approve'}
        title="Approve this merchant?"
        message={`${merchant.businessName} will be able to log in, create API credentials and send orders.`}
        confirmText="Approve"
        loading={busy}
        onConfirm={() => simpleAction.mutate('approve')}
        onCancel={() => setDialog(null)}
      />
      <ConfirmDialog
        open={dialog === 'reinstate'}
        title="Reinstate this merchant?"
        message={`${merchant.businessName} will be able to log in and use the API again.`}
        confirmText="Reinstate"
        loading={busy}
        onConfirm={() => simpleAction.mutate('reinstate')}
        onCancel={() => setDialog(null)}
      />
      <ReasonDialog
        open={dialog === 'reject'}
        title="Reject this merchant?"
        message={`${merchant.businessName} will not be able to log in. You can approve the registration later if needed.`}
        confirmText="Reject merchant"
        onSubmit={submitReason('reject')}
        onStale={handleStale}
        onCancel={() => setDialog(null)}
      />
      <ReasonDialog
        open={dialog === 'suspend'}
        title="Suspend this merchant?"
        message={`${merchant.businessName} is logged out and cannot log in, get API tokens or create orders. Existing parcels continue their delivery. You can reinstate the merchant later.`}
        confirmText="Suspend merchant"
        onSubmit={submitReason('suspend')}
        onStale={handleStale}
        onCancel={() => setDialog(null)}
      />
      <DiscountDialog
        open={dialog === 'discount'}
        currentPercent={merchant.discountPercent}
        onSubmit={submitDiscount}
        onCancel={() => setDialog(null)}
      />
    </Box>
  );
}
