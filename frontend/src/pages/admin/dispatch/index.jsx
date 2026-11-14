import React, { useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import Pagination from '@mui/material/TablePagination';
import Paper from '@mui/material/Paper';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import { assignDelivery, listCouriers, listDeliveries } from '../../../api/adminDelivery';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import { tokens } from '../../../theme/theme';
import { formatDateTime } from '../../../utils/format';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card };

function formatAge(createdAt) {
  const createdTime = new Date(createdAt).getTime();
  if (!Number.isFinite(createdTime)) return '—';

  const minutes = Math.max(0, Math.floor((Date.now() - createdTime) / 60000));
  if (minutes < 1) return 'Less than a minute ago';
  if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;

  const days = Math.floor(hours / 24);
  return `${days} day${days === 1 ? '' : 's'} ago`;
}

function Field({ label, value }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="caption" sx={{ display: 'block', color: tokens.palette.mutedText }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ overflowWrap: 'anywhere', color: tokens.palette.text }}>
        {value || '—'}
      </Typography>
    </Box>
  );
}

function statusLabel(status) {
  return status.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function CourierOption({ courier, selected }) {
  const counts = Object.entries(courier.activeDeliveries || {});
  const workload = counts.reduce((total, [, count]) => total + count, 0);

  return (
    <Paper
      variant="outlined"
      sx={{
        mb: 1,
        borderColor: selected ? tokens.palette.primary : tokens.palette.border,
        backgroundColor: selected ? tokens.palette.softOrange : tokens.palette.surface,
      }}
    >
      <FormControlLabel
        value={courier.id}
        control={<Radio />}
        sx={{ alignItems: 'flex-start', m: 0, p: 1.5, width: '100%' }}
        label={(
          <Box sx={{ py: 0.5, minWidth: 0 }}>
            <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{courier.fullName}</Typography>
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
              {courier.vehicleType} · {courier.vehicleNumber}
            </Typography>
            <Typography variant="body2" sx={{ mt: 0.75 }}>
              Active delivery workload: {workload}
            </Typography>
            <Typography variant="caption" sx={{ display: 'block', color: tokens.palette.mutedText }}>
              {counts.map(([status, count]) => `${statusLabel(status)}: ${count}`).join(' · ')}
            </Typography>
          </Box>
        )}
      />
    </Paper>
  );
}

export default function AdminDispatchPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [assignmentParcel, setAssignmentParcel] = useState(null);
  const [selectedCourierId, setSelectedCourierId] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [assignmentError, setAssignmentError] = useState(null);
  const [notice, setNotice] = useState(null);

  const unassignedQuery = useQuery({
    queryKey: ['admin', 'deliveries', { assigned: false, status: 'CREATED', page, size }],
    queryFn: () => listDeliveries({ assigned: false, status: 'CREATED', page, size }),
    placeholderData: keepPreviousData,
  });
  const assignedQuery = useQuery({
    queryKey: ['admin', 'deliveries', { assigned: true, status: 'CREATED', page, size }],
    queryFn: () => listDeliveries({ assigned: true, status: 'CREATED', page, size }),
    placeholderData: keepPreviousData,
  });
  const couriersQuery = useQuery({
    queryKey: ['admin', 'couriers'],
    queryFn: listCouriers,
    enabled: Boolean(assignmentParcel),
  });
  const activeCouriers = (couriersQuery.data || []).filter((courier) => courier.status === 'ACTIVE');
  const selectedCourier = activeCouriers.find((courier) => courier.id === selectedCourierId);

  const assignMutation = useMutation({
    mutationFn: ({ parcelId, courierId }) => assignDelivery(parcelId, courierId),
    onSuccess: (_result, variables) => {
      const courier = activeCouriers.find((item) => item.id === variables.courierId);
      setNotice(`Assigned the parcel to ${courier?.fullName || 'the selected courier'}.`);
      setAssignmentParcel(null);
      setSelectedCourierId('');
      setConfirming(false);
      setAssignmentError(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'couriers'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'courier-deliveries'] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard', 'dispatch'] });
    },
    onError: (error) => {
      if (error?.code === 'PARCEL_NOT_ASSIGNABLE') {
        setAssignmentError('This parcel can no longer be assigned');
      } else if (error?.status === 404) {
        setAssignmentError('The parcel or courier was not found. The queue has been refreshed; please try again.');
      } else {
        setAssignmentError(error?.detail || 'The parcel could not be assigned. Please try again.');
      }

      if (error?.code === 'PARCEL_NOT_ASSIGNABLE' || error?.status === 404 || error?.status === 409) {
        queryClient.invalidateQueries({ queryKey: ['admin', 'deliveries'] });
        queryClient.invalidateQueries({ queryKey: ['admin', 'couriers'] });
      }
    },
  });

  const closeAssignment = () => {
    if (assignMutation.isPending) return;
    setAssignmentParcel(null);
    setSelectedCourierId('');
    setConfirming(false);
    setAssignmentError(null);
  };

  const queueQuery = tab === 0 ? unassignedQuery : assignedQuery;
  const parcels = queueQuery.data?.items || [];
  const tabLabel = tab === 0 ? 'Unassigned' : 'Assigned, not picked up';

  const openAssignment = (parcel) => {
    setNotice(null);
    setAssignmentError(null);
    setAssignmentParcel(parcel);
    setSelectedCourierId('');
    setConfirming(false);
  };

  const changeTab = (_event, nextTab) => {
    setTab(nextTab);
    setPage(0);
  };

  const submitAssignment = () => {
    if (!assignmentParcel || !selectedCourier) return;
    assignMutation.mutate({
      parcelId: assignmentParcel.parcelId,
      courierId: selectedCourier.id,
    });
  };

  return (
    <Box>
      <PageHeader title="Dispatch" subtitle="Assign and reassign parcels to active couriers." />

      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}

      <Card elevation={0} sx={cardSx}>
        <Tabs
          value={tab}
          onChange={changeTab}
          aria-label="Dispatch queue"
          variant="fullWidth"
          sx={{ borderBottom: `1px solid ${tokens.palette.border}` }}
        >
          <Tab id="dispatch-tab-unassigned" aria-controls="dispatch-panel" label="Unassigned"
            sx={{ minWidth: 0, whiteSpace: 'normal' }} />
          <Tab id="dispatch-tab-assigned" aria-controls="dispatch-panel" label="Assigned, not picked up"
            sx={{ minWidth: 0, whiteSpace: 'normal' }} />
        </Tabs>

        <Box role="tabpanel" id="dispatch-panel" aria-labelledby={tab === 0 ? 'dispatch-tab-unassigned' : 'dispatch-tab-assigned'}>
          <CardContent sx={{ p: { xs: 1.5, sm: 2.5 } }}>
            <Typography variant="h6" sx={{ fontSize: '1rem', fontWeight: 700, mb: 2 }}>
              {tabLabel}
            </Typography>

            {queueQuery.isPending && <LoadingState message={`Loading ${tabLabel.toLowerCase()} deliveries...`} />}
            {queueQuery.isError && (
              <ErrorState
                message={queueQuery.error?.detail || `Failed to load ${tabLabel.toLowerCase()} deliveries.`}
                onRetry={() => queueQuery.refetch()}
              />
            )}
            {queueQuery.isSuccess && parcels.length === 0 && (
              <EmptyState
                title={tab === 0 ? 'No unassigned parcels' : 'No assigned parcels awaiting pickup'}
                description={tab === 0
                  ? 'There are no created parcels waiting for a courier.'
                  : 'There are no assigned parcels waiting for pickup.'}
                minHeight={200}
              />
            )}
            {queueQuery.isSuccess && parcels.length > 0 && (
              <>
                <Box sx={{ display: 'grid', gap: 1.5 }}>
                  {parcels.map((parcel) => (
                    <Card
                      key={parcel.parcelId}
                      component="article"
                      elevation={0}
                      aria-label={`Parcel ${parcel.trackingNumber}`}
                      sx={{ ...cardSx, backgroundColor: tokens.palette.surface }}
                    >
                      <CardContent sx={{ p: { xs: 1.5, sm: 2 } }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5, overflowWrap: 'anywhere' }}>
                          {parcel.trackingNumber}
                        </Typography>
                        <Box sx={{
                          display: 'grid',
                          gap: 1.5,
                          gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' },
                        }}>
                          <Field label="Origin district" value={parcel.originDistrictName} />
                          <Field label="Destination district" value={parcel.destinationDistrictName} />
                          <Field label="Pickup city" value={parcel.pickupCity} />
                          <Field label="Drop-off city" value={parcel.dropoffCity} />
                          <Field label="Created" value={formatDateTime(parcel.parcelCreatedAt)} />
                          <Field label="Age since creation" value={formatAge(parcel.parcelCreatedAt)} />
                        </Box>
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
                          <Button
                            variant={tab === 0 ? 'contained' : 'outlined'}
                            onClick={() => openAssignment(parcel)}
                            sx={{ width: { xs: '100%', sm: 'auto' } }}
                          >
                            {tab === 0 ? 'Assign' : 'Reassign'}
                          </Button>
                        </Box>
                      </CardContent>
                    </Card>
                  ))}
                </Box>
                <Pagination
                  component="div"
                  count={queueQuery.data?.totalItems || 0}
                  page={page}
                  rowsPerPage={size}
                  rowsPerPageOptions={[10, 20, 50, 100]}
                  onPageChange={(_event, nextPage) => setPage(nextPage)}
                  onRowsPerPageChange={(event) => {
                    setSize(Number(event.target.value));
                    setPage(0);
                  }}
                  labelRowsPerPage="Rows:"
                />
              </>
            )}
          </CardContent>
        </Box>
      </Card>

      <Dialog
        open={Boolean(assignmentParcel)}
        onClose={closeAssignment}
        fullWidth
        maxWidth="sm"
        aria-labelledby="assign-courier-dialog-title"
      >
        <DialogTitle id="assign-courier-dialog-title">
          {tab === 0 ? 'Assign courier' : 'Reassign courier'}
        </DialogTitle>
        <DialogContent dividers>
          {assignmentParcel && (
            <Typography variant="body2" sx={{ mb: 2, overflowWrap: 'anywhere' }}>
              Parcel: <strong>{assignmentParcel.trackingNumber}</strong>
            </Typography>
          )}
          {couriersQuery.isPending && <LoadingState message="Loading active couriers..." minHeight={120} />}
          {couriersQuery.isError && (
            <ErrorState
              message={couriersQuery.error?.detail || 'Failed to load couriers.'}
              onRetry={() => couriersQuery.refetch()}
              minHeight={180}
            />
          )}
          {couriersQuery.isSuccess && activeCouriers.length === 0 && (
            <EmptyState
              title="No active couriers"
              description="There are no active couriers available for assignment."
              minHeight={180}
            />
          )}
          {couriersQuery.isSuccess && activeCouriers.length > 0 && !confirming && (
            <FormControl component="fieldset" fullWidth>
              <Typography component="legend" variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
                Select an active courier
              </Typography>
              <RadioGroup
                aria-label="Select an active courier"
                value={selectedCourierId}
                onChange={(event) => {
                  setSelectedCourierId(event.target.value);
                  setAssignmentError(null);
                }}
              >
                {activeCouriers.map((courier) => (
                  <CourierOption key={courier.id} courier={courier} selected={selectedCourierId === courier.id} />
                ))}
              </RadioGroup>
            </FormControl>
          )}
          {confirming && selectedCourier && (
            <Typography>
              Confirm assigning this parcel to <strong>{selectedCourier.fullName}</strong>?
            </Typography>
          )}
          {assignmentError && <Alert severity="error" role="alert" sx={{ mt: 2 }}>{assignmentError}</Alert>}
        </DialogContent>
        <DialogActions sx={{ flexWrap: 'wrap', p: 2 }}>
          <Button onClick={closeAssignment} disabled={assignMutation.isPending}>
            Cancel
          </Button>
          {confirming ? (
            <>
              <Button onClick={() => setConfirming(false)} disabled={assignMutation.isPending}>
                Change courier
              </Button>
              <Button
                variant="contained"
                onClick={submitAssignment}
                disabled={!selectedCourier || assignMutation.isPending}
              >
                {assignMutation.isPending ? 'Assigning...' : 'Confirm assignment'}
              </Button>
            </>
          ) : (
            <Button
              variant="contained"
              onClick={() => { setConfirming(true); setAssignmentError(null); }}
              disabled={!selectedCourier || assignMutation.isPending}
            >
              Review assignment
            </Button>
          )}
        </DialogActions>
      </Dialog>
    </Box>
  );
}
