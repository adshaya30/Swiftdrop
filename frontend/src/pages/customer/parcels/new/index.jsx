import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { Alert, Box, Button, MenuItem, Stack, TextField, Typography } from '@mui/material';
import LoadingState from '../../../../components/LoadingState';
import ErrorState from '../../../../components/ErrorState';
import PageHeader from '../../../../components/PageHeader';
import { getDistricts } from '../../../../api/quotes';
import { createParcel } from '../../../../api/customerParcels';
import { applyServerErrors, formatMoney } from '../../../../utils/format';
import { createDeliveryResolver, createDeliverySchema } from './deliverySchema';
import { useDeliveryQuote } from './useDeliveryQuote';
import './wizard.css';

const steps = ['Details', 'Quote', 'Confirm'];
const addressDefaults = { contactName: '', contactPhone: '', addressLine1: '', addressLine2: '', city: '', districtCode: '', postalCode: '' };
const feeFields = [['baseFee', 'Base fee'], ['weightFee', 'Weight fee'], ['sizeSurcharge', 'Size surcharge'], ['discountAmount', 'Discount'], ['total', 'Total']];
const completeFee = (fee) => fee?.currency === 'LKR' && feeFields.every(([field]) =>
  (typeof fee[field] === 'string' || typeof fee[field] === 'number') && /^\d+(\.\d{1,2})?$/.test(String(fee[field])));

function FeeBreakdown({ fee }) {
  return <dl className="customer-wizard-fees">{feeFields.map(([field, label]) => <React.Fragment key={field}>
    <dt>{label}</dt><dd>{formatMoney(fee[field])}</dd>
  </React.Fragment>)}</dl>;
}

function quoteMessage(error) {
  if (error?.code === 'ZONE_NOT_SERVICEABLE') return 'One of the selected districts is not currently serviceable. Please change the districts.';
  if (error?.code === 'PRICING_NOT_CONFIGURED') return 'Pricing is not available for this route. Please choose another route or try later.';
  if (error?.errors?.some(({ field }) => field === 'weightKg' || field === 'parcel.weightKg')) return 'Please check the parcel weight and request a new quote.';
  return error?.detail || 'We could not obtain a quotation. Please try again.';
}

export default function NewParcelPage() {
  const [reference, setReference] = useState(null);
  const [error, setError] = useState(null);
  const [load, setLoad] = useState(0);
  useEffect(() => {
    let current = true;
    setError(null);
    setReference(null);
    getDistricts().then((data) => {
      createDeliverySchema(data); // Do not open the form with missing/invalid limits.
      if (current) setReference(data);
    }).catch((failure) => { if (current) setError(failure); });
    return () => { current = false; };
  }, [load]);
  if (error) return <ErrorState title="Delivery options unavailable" message="We could not load the delivery options. Please try again." onRetry={() => setLoad((value) => value + 1)} />;
  if (!reference) return <LoadingState message="Loading delivery options…" />;
  return <DeliveryWizard reference={reference} />;
}

function DeliveryWizard({ reference }) {
  const navigate = useNavigate();
  const resolver = useMemo(() => createDeliveryResolver(reference), [reference]);
  const { register, handleSubmit, setError, setFocus, formState: { errors } } = useForm({
    resolver, shouldUnregister: false,
    defaultValues: { pickup: { ...addressDefaults }, dropoff: { ...addressDefaults, contactEmail: '' }, parcel: { weightKg: '', size: '', description: '' }, deliveryInstructions: '' },
  });
  const [step, setStep] = useState(0);
  const [details, setDetails] = useState(null);
  const [revision, setRevision] = useState(0);
  const quote = useDeliveryQuote(details, step === 1, revision);
  const [replacementFee, setReplacementFee] = useState(null);
  const [attempt, setAttempt] = useState(null);
  const [pending, setPending] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [message, setMessage] = useState('');
  const busy = useRef(false);
  const heading = useRef(null);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => { heading.current?.focus(); }, [step]);
  const fee = replacementFee || (quote.status === 'success' ? quote.fee : null);

  const backToDetails = () => {
    setReplacementFee(null);
    setAttempt(null);
    setDetails(null);
    setRevision((value) => value + 1);
    setMessage('');
    setStep(0);
  };
  const showQuote = (values) => {
    setDetails(values);
    setReplacementFee(null);
    setAttempt(null);
    setMessage('');
    setStep(1);
  };
  const review = () => {
    if (!completeFee(fee)) return;
    try {
      // FR-PAR-006: freeze body/key together for this confirmation screen.
      setAttempt({ key: crypto.randomUUID(), body: JSON.parse(JSON.stringify({ ...details, expectedFee: fee.total })), fee });
      setMessage('');
      setStep(2);
    } catch {
      setMessage('Secure confirmation could not be prepared. Please try again in a supported browser.');
    }
  };
  const submit = async () => {
    if (busy.current || !attempt) return;
    busy.current = true;
    setPending(true);
    setMessage('');
    try {
      const created = await createParcel(attempt.body, attempt.key);
      if (!created?.id || created.status !== 'CREATED') throw new Error('Unconfirmed creation response');
      if (!mounted.current) return;
      navigate(`/customer/parcels/${encodeURIComponent(created.id)}`, { replace: true });
      // Keep locked while navigation completes, preventing a second creation.
    } catch (error) {
      if (!mounted.current) return;
      if (error.status === 409 && error.code === 'FEE_CHANGED') {
        setUncertain(false);
        setAttempt(null);
        setRevision((value) => value + 1);
        setReplacementFee(completeFee(error.fee) ? error.fee : null);
        setStep(1);
        setMessage(completeFee(error.fee)
          ? 'The delivery fee changed. Review the updated fee before confirming again.'
          : 'The delivery fee changed, but the updated fee was unavailable. Requesting a fresh quotation; review it before confirming again.');
      } else if (error.status >= 400 && error.status < 500) {
        setUncertain(false);
        setMessage(error.detail || 'The request was rejected. Please review the details.');
        if (error.errors?.length) {
          applyServerErrors(setError, { ...error, errors: error.errors.map((item) => ({ ...item, field: item.field === 'weightKg' ? 'parcel.weightKg' : item.field })) });
          setAttempt(null);
          setStep(0);
          const firstField = error.errors[0].field;
          setTimeout(() => setFocus(firstField === 'weightKg' ? 'parcel.weightKg' : firstField), 0);
        }
      } else {
        // Network/5xx may follow a committed transaction. Never change body/key.
        setUncertain(true);
        setMessage('We could not confirm whether your parcel was created. Retry this same request to check safely. Keep this page open; do not start another delivery.');
      }
      busy.current = false;
      setPending(false);
    }
  };

  const field = (path, label, options = {}) => {
    const problem = path.split('.').reduce((object, key) => object?.[key], errors);
    return <TextField key={path} {...register(path)} label={label} fullWidth variant="outlined"
      {...options} error={Boolean(problem)} helperText={problem?.message || options.helperText} />;
  };
  const address = (prefix, title) => <fieldset><legend>{title}</legend><div className="customer-wizard-grid">
    {field(`${prefix}.contactName`, 'Contact name', { required: true, inputProps: { maxLength: 100 } })}
    {field(`${prefix}.contactPhone`, 'Phone', { required: true, type: 'tel' })}
    {field(`${prefix}.addressLine1`, 'Address line 1', { required: true, inputProps: { maxLength: 150 } })}
    {field(`${prefix}.addressLine2`, 'Address line 2 (optional)', { inputProps: { maxLength: 150 } })}
    {field(`${prefix}.city`, 'City', { required: true, inputProps: { maxLength: 80 } })}
    {field(`${prefix}.districtCode`, 'District', { required: true, select: true, defaultValue: '', children: reference.districts.map((district) => <MenuItem key={district.code} value={district.code}>{district.name || district.code}</MenuItem>) })}
    {field(`${prefix}.postalCode`, 'Postal code (optional)', { inputProps: { inputMode: 'numeric', maxLength: 5 } })}
    {prefix === 'dropoff' && field('dropoff.contactEmail', 'Email (optional)', { type: 'email', inputProps: { maxLength: 254 } })}
  </div></fieldset>;

  return <Box className="customer-wizard">
    <PageHeader title="New delivery" />
    <ol className="customer-wizard-steps" aria-label="Delivery steps">{steps.map((name, index) => <li key={name} aria-current={step === index ? 'step' : undefined}>{index + 1}. {name}</li>)}</ol>
    <Typography variant="h5" component="h2" tabIndex={-1} ref={heading}>{steps[step]}</Typography>
    {message && <Alert severity={uncertain ? 'warning' : 'info'} role="alert">{message}</Alert>}
    {step === 0 && <form noValidate onSubmit={handleSubmit(showQuote)}>
      <p>Fields marked * are required.</p>
      {address('pickup', 'Pickup')}{address('dropoff', 'Drop-off')}
      <fieldset><legend>Parcel</legend><div className="customer-wizard-grid">
        {field('parcel.weightKg', 'Weight (kg)', { required: true, helperText: `Maximum ${reference.maxWeightKg} kg; up to two decimal places`, inputProps: { inputMode: 'decimal' } })}
        {field('parcel.size', 'Parcel size', { required: true, select: true, defaultValue: '', children: reference.sizes.map((size) => <MenuItem key={size.size} value={size.size}>{size.size} — {size.description} ({formatMoney(size.surcharge)})</MenuItem>) })}
        {field('parcel.description', 'Description (optional)', { multiline: true, inputProps: { maxLength: 200 } })}
        {field('deliveryInstructions', 'Delivery instructions (optional)', { multiline: true, inputProps: { maxLength: 300 } })}
      </div></fieldset>
      <Button type="submit" variant="contained">Get quote</Button>
    </form>}
    {step === 1 && <Stack spacing={2} aria-live="polite">
      {!fee && quote.status !== 'error' && <LoadingState message="Preparing your quotation…" />}
      {!replacementFee && quote.status === 'error' && <ErrorState title="Quotation unavailable" message={quoteMessage(quote.error)} onRetry={() => setRevision((value) => value + 1)} />}
      {fee && completeFee(fee) && <FeeBreakdown fee={fee} />}
      {fee && !completeFee(fee) && <Alert severity="error">The quotation was incomplete. Please request a new quote.</Alert>}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button onClick={backToDetails}>Edit details</Button>
        <Button onClick={() => { setReplacementFee(null); setRevision((value) => value + 1); }}>Refresh quote</Button>
        <Button variant="contained" disabled={!completeFee(fee)} onClick={review}>Review delivery</Button>
      </Stack>
    </Stack>}
    {step === 2 && attempt && <Stack spacing={2}>
      <Typography>Review your delivery before confirming.</Typography>
      {['pickup', 'dropoff'].map((prefix) => <section key={prefix} aria-label={`${prefix === 'pickup' ? 'Pickup' : 'Drop-off'} summary`}>
        <h3>{prefix === 'pickup' ? 'Pickup' : 'Drop-off'}</h3>
        <p>{details[prefix].contactName} · {details[prefix].contactPhone}</p>
        <p>{[details[prefix].addressLine1, details[prefix].addressLine2, details[prefix].city, reference.districts.find(({ code }) => code === details[prefix].districtCode)?.name || details[prefix].districtCode, details[prefix].postalCode].filter(Boolean).join(', ')}</p>
        {details[prefix].contactEmail && <p>{details[prefix].contactEmail}</p>}
      </section>)}
      <p>{details.parcel.size} · {details.parcel.weightKg} kg</p>
      {details.parcel.description && <p>{details.parcel.description}</p>}
      {details.deliveryInstructions && <p>{details.deliveryInstructions}</p>}
      <FeeBreakdown fee={attempt.fee} />
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button disabled={pending || uncertain} onClick={backToDetails}>Edit details</Button>
        <Button variant="contained" disabled={pending} onClick={submit}>{pending ? 'Submitting…' : uncertain ? 'Retry same request' : 'Confirm delivery'}</Button>
      </Stack>
    </Stack>}
  </Box>;
}
