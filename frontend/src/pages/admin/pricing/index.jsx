import React, { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
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
import {
  getPricingMatrix, getSizeSurcharges, listZones, setPricing, setSizeSurcharges,
} from '../../../api/adminPricing';
import ConfirmDialog from '../../../components/ConfirmDialog';
import ErrorState from '../../../components/ErrorState';
import LoadingState from '../../../components/LoadingState';
import PageHeader from '../../../components/PageHeader';
import { tokens } from '../../../theme/theme';
import { formatMoney } from '../../../utils/format';
import { toInputValue, toRequestAmount, validateFee } from './feeValidation';

const cardSx = { border: `1px solid ${tokens.palette.border}`, borderRadius: tokens.radius.card };
const SIZE_ORDER = ['SMALL', 'MEDIUM', 'LARGE'];
const SIZE_LABEL = { SMALL: 'Small', MEDIUM: 'Medium', LARGE: 'Large' };

const messageOf = (err) => err?.errors?.[0]?.message || err?.detail || 'The change could not be saved.';

function FeeField({ label, value, onChange, error }) {
  return (
    <TextField
      size="small"
      value={value}
      onChange={onChange}
      error={Boolean(error)}
      helperText={error || ' '}
      inputProps={{ 'aria-label': label, inputMode: 'decimal', maxLength: 10 }}
      sx={{ minWidth: 120 }}
    />
  );
}

/**
 * FR-PRC-007: pricing matrix and size surcharges. Fees are shown and sent as the server defines them; the page never
 * calculates a fee. Every save needs a confirmation, and changes affect future quotes only (BR-09).
 */
export default function AdminPricingPage() {
  const queryClient = useQueryClient();
  const [edits, setEdits] = useState({});
  const [sizeEdits, setSizeEdits] = useState({});
  const [pending, setPending] = useState(null); // { kind: 'pair' | 'sizes', ... }
  const [notice, setNotice] = useState(null);
  const [saveError, setSaveError] = useState(null);

  const zonesQuery = useQuery({ queryKey: ['admin', 'zones'], queryFn: listZones });
  const matrixQuery = useQuery({ queryKey: ['admin', 'pricing'], queryFn: getPricingMatrix });
  const sizesQuery = useQuery({ queryKey: ['admin', 'size-surcharges'], queryFn: getSizeSurcharges });

  const zones = zonesQuery.data;
  const matrix = matrixQuery.data;

  const byPair = useMemo(() => {
    const map = new Map();
    (matrix || []).forEach((row) => map.set(`${row.originZoneId}:${row.destinationZoneId}`, row));
    return map;
  }, [matrix]);

  const pairs = useMemo(() => {
    if (!zones) return [];
    const sorted = [...zones].sort((a, b) => a.name.localeCompare(b.name));
    return sorted.flatMap((origin) => sorted.map((destination) => ({
      key: `${origin.id}:${destination.id}`, origin, destination, current: byPair.get(`${origin.id}:${destination.id}`),
    })));
  }, [zones, byPair]);

  const pairValues = (pair) => edits[pair.key] || {
    baseFee: toInputValue(pair.current?.baseFee), perKgFee: toInputValue(pair.current?.perKgFee),
  };
  const pairErrors = (pair) => {
    if (!edits[pair.key]) return {};
    const v = edits[pair.key];
    return { baseFee: validateFee(v.baseFee), perKgFee: validateFee(v.perKgFee) };
  };
  const setPairField = (pair, field) => (event) => {
    setEdits((e) => ({ ...e, [pair.key]: { ...pairValues(pair), ...e[pair.key], [field]: event.target.value } }));
  };

  const sizeRows = useMemo(
    () => [...(sizesQuery.data || [])].sort((a, b) => SIZE_ORDER.indexOf(a.size) - SIZE_ORDER.indexOf(b.size)),
    [sizesQuery.data],
  );
  const sizeValue = (row) => (sizeEdits[row.size] !== undefined ? sizeEdits[row.size] : toInputValue(row.surcharge));
  const sizeError = (row) => (sizeEdits[row.size] !== undefined ? validateFee(sizeEdits[row.size]) : null);
  const sizesDirty = sizeRows.some((r) => sizeEdits[r.size] !== undefined);
  const sizesValid = sizeRows.every((r) => !sizeError(r));

  const pairMutation = useMutation({
    mutationFn: ({ pair, values }) => setPricing(pair.origin.id, pair.destination.id, {
      baseFee: toRequestAmount(values.baseFee), perKgFee: toRequestAmount(values.perKgFee),
    }),
  });
  const sizeMutation = useMutation({
    mutationFn: (rows) => setSizeSurcharges(rows.map((r) => ({ size: r.size, surcharge: toRequestAmount(sizeValue(r)) }))),
  });

  const confirm = async () => {
    const action = pending;
    setSaveError(null);
    setNotice(null);
    try {
      if (action.kind === 'pair') {
        await pairMutation.mutateAsync({ pair: action.pair, values: action.values });
        setEdits((e) => { const next = { ...e }; delete next[action.pair.key]; return next; });
        queryClient.invalidateQueries({ queryKey: ['admin', 'pricing'] });
        setNotice(`Pricing for ${action.pair.origin.name} to ${action.pair.destination.name} was saved.`);
      } else {
        await sizeMutation.mutateAsync(sizeRows);
        setSizeEdits({});
        queryClient.invalidateQueries({ queryKey: ['admin', 'size-surcharges'] });
        setNotice('The size surcharges were saved.');
      }
    } catch (err) {
      setSaveError(messageOf(err));
    } finally {
      setPending(null);
    }
  };

  const confirmMessage = () => {
    if (!pending) return '';
    const tail = ' This affects future quotes and parcels only. Existing parcels keep their fee.';
    if (pending.kind === 'pair') {
      const { pair, values } = pending;
      const was = pair.current
        ? `from ${formatMoney(pair.current.baseFee)} base and ${formatMoney(pair.current.perKgFee)} per kg `
        : '(not configured before) ';
      return `Set ${pair.origin.name} to ${pair.destination.name} ${was}to ${formatMoney(values.baseFee)} base and `
        + `${formatMoney(values.perKgFee)} per kg.${tail}`;
    }
    const changes = sizeRows
      .filter((r) => sizeEdits[r.size] !== undefined)
      .map((r) => `${SIZE_LABEL[r.size]}: ${formatMoney(r.surcharge)} to ${formatMoney(sizeEdits[r.size])}`)
      .join('; ');
    return `Change the size surcharges: ${changes}.${tail}`;
  };

  return (
    <Box>
      <PageHeader title="Pricing" subtitle="Fees per zone pair and surcharges per parcel size. Changes affect future quotes only." />

      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}
      {saveError && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setSaveError(null)}>{saveError}</Alert>}

      <Card elevation={0} sx={{ ...cardSx, mb: 3 }} component="section" aria-label="Zone pricing">
        <CardContent>
          <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem', mb: 2 }}>Zone pricing (LKR)</Typography>
          {(zonesQuery.isPending || matrixQuery.isPending) && <LoadingState message="Loading pricing..." />}
          {(zonesQuery.isError || matrixQuery.isError) && (
            <ErrorState
              message={(zonesQuery.error || matrixQuery.error)?.detail || 'Failed to load the pricing matrix.'}
              onRetry={() => { zonesQuery.refetch(); matrixQuery.refetch(); }}
            />
          )}
          {zonesQuery.isSuccess && matrixQuery.isSuccess && (
            <TableContainer sx={{ overflowX: 'auto' }}>
              <Table size="small" aria-label="Pricing matrix">
                <TableHead>
                  <TableRow>
                    <TableCell>Origin zone</TableCell>
                    <TableCell>Destination zone</TableCell>
                    <TableCell>Base fee</TableCell>
                    <TableCell>Per-kg fee</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {pairs.map((pair) => {
                    const values = pairValues(pair);
                    const errors = pairErrors(pair);
                    const dirty = Boolean(edits[pair.key]);
                    const valid = dirty && !errors.baseFee && !errors.perKgFee;
                    const name = `${pair.origin.name} to ${pair.destination.name}`;
                    return (
                      <TableRow key={pair.key} hover>
                        <TableCell>{pair.origin.name}</TableCell>
                        <TableCell>
                          {pair.destination.name}
                          {!pair.current && (
                            <Typography variant="caption" sx={{ display: 'block', color: tokens.palette.mutedText }}>
                              Not configured
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          <FeeField label={`Base fee ${name}`} value={values.baseFee}
                            onChange={setPairField(pair, 'baseFee')} error={errors.baseFee} />
                        </TableCell>
                        <TableCell>
                          <FeeField label={`Per-kg fee ${name}`} value={values.perKgFee}
                            onChange={setPairField(pair, 'perKgFee')} error={errors.perKgFee} />
                        </TableCell>
                        <TableCell align="right">
                          <Button size="small" variant="contained" disabled={!valid} aria-label={`Save ${name}`}
                            onClick={() => setPending({ kind: 'pair', pair, values })}>
                            Save
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      <Card elevation={0} sx={cardSx} component="section" aria-label="Size surcharges">
        <CardContent>
          <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1rem', mb: 2 }}>Size surcharges (LKR)</Typography>
          {sizesQuery.isPending && <LoadingState message="Loading surcharges..." minHeight={120} />}
          {sizesQuery.isError && (
            <ErrorState message={sizesQuery.error?.detail || 'Failed to load the size surcharges.'}
              onRetry={() => sizesQuery.refetch()} minHeight={120} />
          )}
          {sizesQuery.isSuccess && (
            <>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
                {sizeRows.map((row) => (
                  <Box key={row.size}>
                    <Typography sx={{ fontWeight: 600 }}>{SIZE_LABEL[row.size]}</Typography>
                    <Typography variant="caption" sx={{ display: 'block', color: tokens.palette.mutedText, mb: 1 }}>
                      {row.description}
                    </Typography>
                    <FeeField label={`${SIZE_LABEL[row.size]} surcharge`} value={sizeValue(row)}
                      onChange={(e) => setSizeEdits((s) => ({ ...s, [row.size]: e.target.value }))}
                      error={sizeError(row)} />
                  </Box>
                ))}
              </Box>
              <Button variant="contained" sx={{ mt: 2 }} disabled={!sizesDirty || !sizesValid}
                onClick={() => setPending({ kind: 'sizes' })}>
                Save surcharges
              </Button>
            </>
          )}
        </CardContent>
      </Card>

      <ConfirmDialog
        open={Boolean(pending)}
        title="Confirm pricing change"
        message={confirmMessage()}
        confirmText="Save pricing"
        loading={pairMutation.isPending || sizeMutation.isPending}
        onConfirm={confirm}
        onCancel={() => setPending(null)}
      />
    </Box>
  );
}
