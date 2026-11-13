import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import { createZone, listZones, moveDistrict, updateZone } from '../../../api/adminPricing';
import ConfirmDialog from '../../../components/ConfirmDialog';
import EmptyState from '../../../components/EmptyState';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import { tokens } from '../../../theme/theme';
import { CreateZoneDialog, MoveDistrictDialog, RenameZoneDialog } from './ZoneDialogs';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card };

/** Friendly text for the business errors of FR-PRC-006. */
function describeError(err) {
  if (err?.code === 'ZONE_IN_USE') {
    return 'This zone still has districts. Move them to another zone before deactivating it.';
  }
  return err?.detail || 'The change could not be saved.';
}

/**
 * FR-PRC-006: zones and districts. Changes affect future quotes only (BR-09). The server enforces ADMIN and the
 * ZONE_IN_USE rule; the page only explains the result.
 */
export default function AdminZonesPage() {
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState(null);
  const [deactivateTarget, setDeactivateTarget] = useState(null);
  const [moveTarget, setMoveTarget] = useState(null);
  const [notice, setNotice] = useState(null);
  const [actionError, setActionError] = useState(null);

  const zonesQuery = useQuery({ queryKey: ['admin', 'zones'], queryFn: listZones });
  const zones = zonesQuery.data || [];

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'zones'] });
    queryClient.invalidateQueries({ queryKey: ['admin', 'pricing'] });
  };

  const createMutation = useMutation({
    // Wrapped so that only the form values reach the API function (TanStack Query may pass extra arguments).
    mutationFn: (values) => createZone(values),
    onSuccess: () => { setNotice('The zone was created.'); setActionError(null); refresh(); },
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, body }) => updateZone(id, body),
    onSuccess: () => { setNotice('The zone was updated.'); setActionError(null); refresh(); },
  });
  const moveMutation = useMutation({
    mutationFn: ({ code, zoneId }) => moveDistrict(code, zoneId),
    onSuccess: () => { setNotice('The district was moved.'); setActionError(null); refresh(); },
  });

  const activate = async (zone) => {
    setNotice(null);
    try {
      await updateMutation.mutateAsync({ id: zone.id, body: { active: true } });
    } catch (err) {
      setActionError(describeError(err));
    }
  };

  const deactivate = async () => {
    const zone = deactivateTarget;
    setNotice(null);
    try {
      await updateMutation.mutateAsync({ id: zone.id, body: { active: false } });
    } catch (err) {
      setActionError(describeError(err));
    } finally {
      setDeactivateTarget(null);
    }
  };

  return (
    <Box>
      <PageHeader
        title="Zones"
        subtitle="Zones group districts for pricing. Changes affect future quotes only."
        action={<Button variant="contained" onClick={() => setCreateOpen(true)}>Create zone</Button>}
      />

      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}
      {actionError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError(null)}>{actionError}</Alert>}

      {zonesQuery.isPending && <LoadingState message="Loading zones..." />}
      {zonesQuery.isError && (
        <ErrorState message={zonesQuery.error?.detail || 'Failed to load zones.'} onRetry={() => zonesQuery.refetch()} />
      )}
      {zonesQuery.isSuccess && zones.length === 0 && (
        <EmptyState title="No zones" description="Create a zone to start grouping districts." />
      )}

      {zonesQuery.isSuccess && zones.length > 0 && (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
          {zones.map((zone) => (
            <Card key={zone.id} elevation={0} sx={cardSx} component="section" aria-label={`Zone ${zone.name}`}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 1, mb: 1 }}>
                  <Box>
                    <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.05rem' }}>{zone.name}</Typography>
                    <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>{zone.code}</Typography>
                  </Box>
                  <Chip size="small" label={zone.active ? 'Active' : 'Inactive'}
                    sx={{ fontWeight: 600, backgroundColor: zone.active ? '#F0FDF4' : tokens.palette.mutedSurface,
                      color: zone.active ? '#16A34A' : tokens.palette.mutedText }} />
                </Box>

                <Box component="ul" aria-label={`Districts in ${zone.name}`} sx={{ listStyle: 'none', p: 0, m: 0, mb: 2 }}>
                  {zone.districts.length === 0 && (
                    <Typography component="li" variant="body2" sx={{ color: tokens.palette.mutedText }}>
                      No districts
                    </Typography>
                  )}
                  {zone.districts.map((d) => (
                    <Box component="li" key={d.code}
                      sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 0.5 }}>
                      <Typography variant="body2">{d.name}</Typography>
                      <Button size="small" onClick={() => setMoveTarget({ district: d, zone })}
                        aria-label={`Move ${d.name}`}>
                        Move
                      </Button>
                    </Box>
                  ))}
                </Box>

                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                  <Button size="small" variant="outlined" onClick={() => setRenameTarget(zone)}
                    aria-label={`Rename ${zone.name}`}>
                    Rename
                  </Button>
                  {zone.active ? (
                    <Button size="small" variant="outlined" color="error" onClick={() => setDeactivateTarget(zone)}
                      aria-label={`Deactivate ${zone.name}`}>
                      Deactivate
                    </Button>
                  ) : (
                    <Button size="small" variant="outlined" onClick={() => activate(zone)}
                      aria-label={`Activate ${zone.name}`}>
                      Activate
                    </Button>
                  )}
                </Box>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      <CreateZoneDialog open={createOpen} onClose={() => setCreateOpen(false)}
        onSubmit={(values) => createMutation.mutateAsync(values)} />
      <RenameZoneDialog zone={renameTarget} onClose={() => setRenameTarget(null)}
        onSubmit={(values) => updateMutation.mutateAsync({ id: renameTarget.id, body: { name: values.name } })} />
      <MoveDistrictDialog target={moveTarget} zones={zones} onClose={() => setMoveTarget(null)}
        onSubmit={(zoneId) => moveMutation.mutateAsync({ code: moveTarget.district.code, zoneId })} />
      <ConfirmDialog
        open={Boolean(deactivateTarget)}
        title="Deactivate zone?"
        message={deactivateTarget
          ? `Districts in ${deactivateTarget.name} can no longer be served. A zone that still has districts cannot be deactivated.`
          : ''}
        confirmText="Deactivate zone"
        isDestructive
        loading={updateMutation.isPending}
        onConfirm={deactivate}
        onCancel={() => setDeactivateTarget(null)}
      />
    </Box>
  );
}
