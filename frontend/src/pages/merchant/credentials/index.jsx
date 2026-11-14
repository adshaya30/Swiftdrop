import React, { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import AddOutlinedIcon from '@mui/icons-material/AddOutlined';
import KeyOutlinedIcon from '@mui/icons-material/KeyOutlined';
import { tokens } from '../../../theme/theme';
import PageHeader from '../../../components/PageHeader';
import LoadingState from '../../../components/LoadingState';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import { ConfirmDialog } from '../../../components/ConfirmDialog';
import { formatDateTime } from '../../../utils/format';
import { listCredentials, revokeCredential } from '../../../api/merchantAccount';
import CreateCredentialDialog from './CreateCredentialDialog';
import CredentialStatusChip from './CredentialStatusChip';
import ApiUsageNote from './ApiUsageNote';

/**
 * API credentials of the logged-in merchant (route /merchant/credentials). SRS v2.1 - FR-AUTH-018, UC-M03.
 * Credentials are only created and revoked (no rotate, and a secret is never shown again after creation).
 */

const MAX_ACTIVE = 2;
const PAGE_SIZE = 20;
const QUERY_KEY = ['merchant', 'credentials'];

const headCellSx = { fontWeight: 700, color: tokens.palette.text, whiteSpace: 'nowrap' };

export default function MerchantCredentialsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [toRevoke, setToRevoke] = useState(null);
  const [revokeError, setRevokeError] = useState(null);

  const query = useQuery({
    queryKey: [...QUERY_KEY, page, PAGE_SIZE],
    queryFn: () => listCredentials({ page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const revokeMutation = useMutation({
    mutationFn: (id) => revokeCredential(id),
    onSuccess: () => {
      setToRevoke(null);
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
    onError: (err) => {
      setToRevoke(null);
      setRevokeError(err?.detail || 'The credential could not be revoked. Please try again.');
      queryClient.invalidateQueries({ queryKey: QUERY_KEY });
    },
  });

  const items = query.data?.items ?? [];
  const totalItems = query.data?.totalItems ?? 0;
  const activeCount = items.filter((credential) => credential.status === 'ACTIVE').length;
  // A hint only: the server is the authority and answers 409 CREDENTIAL_LIMIT_REACHED (handled in the dialog).
  const limitReached = activeCount >= MAX_ACTIVE;

  const createButton = (
    <Button
      variant="contained"
      startIcon={<AddOutlinedIcon />}
      onClick={() => setCreating(true)}
      disabled={limitReached}
      aria-describedby={limitReached ? 'credential-limit-note' : undefined}
      sx={{
        backgroundColor: tokens.palette.primary,
        minHeight: tokens.controlHeight.standard,
        fontWeight: 600,
        '&:hover': { backgroundColor: tokens.palette.primaryHover },
      }}
    >
      Create credential
    </Button>
  );

  let content;
  if (query.isPending) {
    content = <LoadingState message="Loading your API credentials..." />;
  } else if (query.isError) {
    content = (
      <ErrorState
        title="Could not load your credentials"
        message={query.error?.detail || 'Please try again.'}
        onRetry={() => query.refetch()}
      />
    );
  } else if (items.length === 0 && page === 0) {
    content = (
      <EmptyState
        icon={KeyOutlinedIcon}
        title="No API credentials yet"
        description="Create a credential to let your own system obtain access tokens and send orders through the API."
        actionLabel="Create credential"
        onAction={() => setCreating(true)}
      />
    );
  } else {
    content = (
      <Paper variant="outlined" sx={{ borderRadius: tokens.radius.card, borderColor: tokens.palette.border }}>
        <TableContainer>
          <Table aria-label="API credentials" size="medium">
            <TableHead>
              <TableRow>
                <TableCell sx={headCellSx}>Label</TableCell>
                <TableCell sx={headCellSx}>Client ID</TableCell>
                <TableCell sx={headCellSx}>Status</TableCell>
                <TableCell sx={headCellSx}>Created</TableCell>
                <TableCell sx={headCellSx}>Last used</TableCell>
                <TableCell sx={headCellSx} align="right">
                  Actions
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((credential) => (
                <TableRow key={credential.id} hover>
                  <TableCell sx={{ fontWeight: 600 }}>{credential.label}</TableCell>
                  <TableCell>
                    <Box component="code" sx={{ fontSize: '0.8125rem', wordBreak: 'break-all' }}>
                      {credential.clientId}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <CredentialStatusChip status={credential.status} />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatDateTime(credential.createdAt)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    {credential.lastUsedAt ? formatDateTime(credential.lastUsedAt) : 'Never'}
                  </TableCell>
                  <TableCell align="right">
                    {credential.status === 'ACTIVE' ? (
                      <Button
                        size="small"
                        variant="outlined"
                        color="error"
                        onClick={() => {
                          setRevokeError(null);
                          setToRevoke(credential);
                        }}
                        aria-label={`Revoke credential ${credential.label}`}
                        sx={{ minHeight: 36 }}
                      >
                        Revoke
                      </Button>
                    ) : (
                      <Typography component="span" variant="body2" sx={{ color: tokens.palette.mutedText }}>
                        —
                      </Typography>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        {totalItems > PAGE_SIZE && (
          <TablePagination
            component="div"
            count={totalItems}
            page={page}
            rowsPerPage={PAGE_SIZE}
            rowsPerPageOptions={[PAGE_SIZE]}
            onPageChange={(_event, next) => setPage(next)}
          />
        )}
      </Paper>
    );
  }

  return (
    <Box>
      <PageHeader
        title="API credentials"
        subtitle="Let your own system obtain access tokens and create orders through the SwiftDrop API."
        action={createButton}
      />

      {limitReached && (
        <Alert id="credential-limit-note" severity="info" sx={{ mb: 2, borderRadius: tokens.radius.control }}>
          You have {MAX_ACTIVE} active credentials, which is the maximum. Revoke one to create another.
        </Alert>
      )}
      {revokeError && (
        <Alert severity="error" onClose={() => setRevokeError(null)} sx={{ mb: 2, borderRadius: tokens.radius.control }}>
          {revokeError}
        </Alert>
      )}

      {content}

      <ApiUsageNote />

      <CreateCredentialDialog open={creating} onClose={() => setCreating(false)} />

      <ConfirmDialog
        open={Boolean(toRevoke)}
        title="Revoke this credential?"
        message={
          toRevoke
            ? `"${toRevoke.label}" (${toRevoke.clientId}) will stop working immediately: systems that use it can no longer obtain access tokens. Tokens already issued stay valid until they expire (at most 15 minutes). This cannot be undone.`
            : ''
        }
        confirmText="Revoke credential"
        isDestructive
        loading={revokeMutation.isPending}
        onConfirm={() => revokeMutation.mutate(toRevoke.id)}
        onCancel={() => setToRevoke(null)}
      />
    </Box>
  );
}
