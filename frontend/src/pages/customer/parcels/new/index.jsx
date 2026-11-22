import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { Button, MenuItem, TextField } from '@mui/material';
import { ArrowLeft, ArrowRight, Check, CircleAlert, Info, MapPin, Package, ReceiptText, RefreshCw, Truck } from 'lucide-react';
import LoadingState from '../../../../components/LoadingState';
import { getDistricts } from '../../../../api/quotes';
import { createParcel } from '../../../../api/customerParcels';
import { applyServerErrors, formatMoney } from '../../../../utils/format';
import { createDeliveryResolver, createDeliverySchema } from './deliverySchema';
import { useDeliveryQuote } from './useDeliveryQuote';
import './wizard.css';

const steps = ['Details', 'Quote', 'Confirm'];
const addressSections = [
  { key: 'pickup', title: 'Pickup', Icon: Truck },
  { key: 'dropoff', title: 'Drop-off', Icon: MapPin },
];
const addressDefaults = { contactName: '', contactPhone: '', addressLine1: '', addressLine2: '', city: '', districtCode: '', postalCode: '' };
const feeFields = [['baseFee', 'Base fee'], ['weightFee', 'Weight fee'], ['sizeSurcharge', 'Size surcharge'], ['discountAmount', 'Discount']];
const completeFee = (fee) => fee?.currency === 'LKR' && [...feeFields.map(([field]) => field), 'total'].every((field) =>
  (typeof fee[field] === 'string' || typeof fee[field] === 'number') && /^\d+(\.\d{1,2})?$/.test(String(fee[field])));

function SectionTitle({ Icon, children, kind = 'blue' }) {
  return <span className="customer-wizard-section-title" data-kind={kind}>
    <span className="customer-wizard-icon"><Icon size={20} aria-hidden="true" /></span>
    {children}
  </span>;
}

function WizardNotice({ title, children, onRetry, attention = false }) {
  const Icon = attention ? CircleAlert : Info;
  return <div className="customer-wizard-notice" role="alert">
    <Icon size={20} aria-hidden="true" />
    <div>
      {title && <h3>{title}</h3>}
      <p>{children}</p>
      {onRetry && <div className="customer-wizard-actions">
        <Button variant="outlined" type="button" onClick={onRetry}>Try Again</Button>
      </div>}
    </div>
  </div>;
}

function FeeBreakdown({ fee, confirmation = false }) {
  return <section className={`customer-wizard-card customer-wizard-price${confirmation ? ' customer-wizard-price-final' : ''}`}>
    <h3><SectionTitle Icon={ReceiptText}>{confirmation ? 'Final price' : 'Quotation summary'}</SectionTitle></h3>
    <dl className="customer-wizard-fees">
      {feeFields.map(([field, label]) => <div key={field} className="customer-wizard-fee-row">
        <dt>{label}</dt><dd>{formatMoney(fee[field])}</dd>
      </div>)}
      <div className="customer-wizard-fee-row customer-wizard-total">
        <dt>Total</dt><dd>{formatMoney(fee.total)}</dd>
      </div>
    </dl>
  </section>;
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
  if (error || !reference) return <div className="customer-wizard">
    <h1 className="customer-wizard-title">New delivery</h1>
    {error ? <WizardNotice title="Delivery options unavailable" attention onRetry={() => setLoad((value) => value + 1)}>
      We could not load the delivery options. Please try again.
    </WizardNotice> : <div className="customer-wizard-card" role="status"><LoadingState message="Loading delivery options…" minHeight={120} /></div>}
  </div>;
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
      className="customer-wizard-field" slotProps={{
        input: { notched: false },
        select: { MenuProps: { slotProps: { paper: { className: 'customer-wizard-menu' } } } },
      }}
      {...options} error={Boolean(problem)} helperText={problem?.message || options.helperText} />;
  };
  const address = ({ key: prefix, title, Icon }) => <fieldset key={prefix} className="customer-wizard-card" data-section={prefix} aria-labelledby={`delivery-${prefix}-title`}>
    <h3 id={`delivery-${prefix}-title`}><SectionTitle Icon={prefix === 'pickup' ? MapPin : Icon} kind={prefix}>{title}</SectionTitle></h3>
    <div className="customer-wizard-address-fields">
      {field(`${prefix}.contactName`, 'Contact name', { required: true, inputProps: { maxLength: 100 } })}
      {field(`${prefix}.contactPhone`, 'Phone', { required: true, type: 'tel' })}
      {field(`${prefix}.addressLine1`, 'Address line 1', { required: true, inputProps: { maxLength: 150 } })}
      {field(`${prefix}.addressLine2`, 'Address line 2 (optional)', { inputProps: { maxLength: 150 } })}
      {field(`${prefix}.city`, 'City', { required: true, inputProps: { maxLength: 80 } })}
      {field(`${prefix}.districtCode`, 'District', { required: true, select: true, defaultValue: '', children: reference.districts.map((district) => <MenuItem key={district.code} value={district.code}>{district.name || district.code}</MenuItem>) })}
      {field(`${prefix}.postalCode`, 'Postal code (optional)', { inputProps: { inputMode: 'numeric', maxLength: 5 } })}
      {prefix === 'dropoff' && field('dropoff.contactEmail', 'Email (optional)', { type: 'email', inputProps: { maxLength: 254 } })}
    </div>
  </fieldset>;

  return <div className="customer-wizard">
    <h1 className="customer-wizard-title">New delivery</h1>
    <ol className="customer-wizard-steps" aria-label="Delivery steps" role="list">
      {steps.map((name, index) => <li key={name} aria-current={step === index ? 'step' : undefined} data-complete={index < step}>
        <span className="customer-wizard-step-number" aria-hidden="true">{index < step ? <Check size={18} /> : index + 1}</span>
        <span>{name}</span>
        {index < step && <span className="customer-wizard-sr-only"> completed</span>}
      </li>)}
    </ol>
    <h2 className="customer-wizard-heading" tabIndex={-1} ref={heading}>{steps[step]}</h2>
    {message && <WizardNotice attention={uncertain}>{message}</WizardNotice>}
    {step === 0 && <form className="customer-wizard-content" noValidate onSubmit={handleSubmit(showQuote)}>
      <p className="customer-wizard-note">Fields marked * are required.</p>
      <div className="customer-wizard-details-addresses">
        {addressSections.map(address)}
      </div>
      <fieldset className="customer-wizard-card" aria-labelledby="delivery-parcel-title">
        <h3 id="delivery-parcel-title"><SectionTitle Icon={Package} kind="parcel">Parcel</SectionTitle></h3>
        <div className="customer-wizard-grid">
          {field('parcel.weightKg', 'Weight (kg)', { required: true, helperText: `Maximum ${reference.maxWeightKg} kg; up to two decimal places`, inputProps: { inputMode: 'decimal' } })}
          {field('parcel.size', 'Parcel size', { required: true, select: true, defaultValue: '', children: reference.sizes.map((size) => <MenuItem key={size.size} value={size.size}>{size.size} — {size.description} ({formatMoney(size.surcharge)})</MenuItem>) })}
          {field('parcel.description', 'Description (optional)', { multiline: true, minRows: 2, inputProps: { maxLength: 200 } })}
          {field('deliveryInstructions', 'Delivery instructions (optional)', { multiline: true, minRows: 2, inputProps: { maxLength: 300 } })}
        </div>
      </fieldset>
      <div className="customer-wizard-actions">
        <Button type="submit" variant="contained" endIcon={<ArrowRight size={18} />}>Get quote</Button>
      </div>
    </form>}
    {step === 1 && <div className="customer-wizard-content" aria-live="polite">
      {!fee && quote.status !== 'error' && <div className="customer-wizard-card"><LoadingState message="Preparing your quotation…" minHeight={120} /></div>}
      {!replacementFee && quote.status === 'error' && <WizardNotice title="Quotation unavailable" attention onRetry={() => setRevision((value) => value + 1)}>{quoteMessage(quote.error)}</WizardNotice>}
      {fee && completeFee(fee) && <FeeBreakdown fee={fee} />}
      {fee && !completeFee(fee) && <WizardNotice attention>The quotation was incomplete. Please request a new quote.</WizardNotice>}
      <div className="customer-wizard-actions">
        <Button variant="outlined" startIcon={<ArrowLeft size={18} />} onClick={backToDetails}>Edit details</Button>
        <Button variant="outlined" startIcon={<RefreshCw size={18} />} onClick={() => { setReplacementFee(null); setRevision((value) => value + 1); }}>Refresh quote</Button>
        <Button variant="contained" endIcon={<ArrowRight size={18} />} disabled={!completeFee(fee)} onClick={review}>Review delivery</Button>
      </div>
    </div>}
    {step === 2 && attempt && <div className="customer-wizard-content">
      <p className="customer-wizard-note">Review your delivery before confirming.</p>
      <div className="customer-wizard-grid customer-wizard-addresses">
        {addressSections.map(({ key, title, Icon }) => {
          const address = details[key];
          const district = reference.districts.find(({ code }) => code === address.districtCode);
          return <section key={key} className="customer-wizard-card customer-wizard-address" data-section={key} aria-label={`${title} summary`}>
            <h3><SectionTitle Icon={Icon} kind={key}>{title}</SectionTitle></h3>
            <p><strong>{address.contactName}</strong></p>
            <p>{address.contactPhone}{address.contactEmail && <><br />{address.contactEmail}</>}</p>
            <p>{[address.addressLine1, address.addressLine2, address.city, district?.name || address.districtCode, address.postalCode].filter(Boolean).join(', ')}</p>
          </section>;
        })}
      </div>
      <section className="customer-wizard-card">
        <h3><SectionTitle Icon={Package} kind="parcel">Parcel summary</SectionTitle></h3>
        <dl className="customer-wizard-parcel">
          <div><dt>Size</dt><dd>{details.parcel.size}</dd></div>
          <div><dt>Weight</dt><dd>{details.parcel.weightKg} kg</dd></div>
          {details.parcel.description && <div className="customer-wizard-parcel-note"><dt>Description</dt><dd>{details.parcel.description}</dd></div>}
          {details.deliveryInstructions && <div className="customer-wizard-parcel-note"><dt>Delivery instructions</dt><dd>{details.deliveryInstructions}</dd></div>}
        </dl>
      </section>
      <FeeBreakdown fee={attempt.fee} confirmation />
      <div className="customer-wizard-actions">
        <Button variant="outlined" startIcon={<ArrowLeft size={18} />} disabled={pending || uncertain} onClick={backToDetails}>Edit details</Button>
        <Button variant="contained" endIcon={<Check size={18} />} disabled={pending} onClick={submit}>{pending ? 'Submitting…' : uncertain ? 'Retry same request' : 'Confirm delivery'}</Button>
      </div>
    </div>}
  </div>;
}
