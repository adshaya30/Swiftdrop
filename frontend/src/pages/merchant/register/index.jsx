import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import HourglassTopOutlinedIcon from '@mui/icons-material/HourglassTopOutlined';
import { tokens } from '../../../theme/theme';
import { registerMerchant } from '../../../api/merchantAccount';
import { applyServerErrors } from '../../../utils/format';
import { passwordRules, registerMerchantSchema, toRequestBody } from './schema';

/**
 * Merchant registration (route /register-merchant). SRS v2.1 - FR-AUTH-016, UC-M01.
 * The account waits for admin approval: no tokens are issued and no login is attempted after registering.
 */

const visuallyHidden = {
  position: 'absolute',
  width: 1,
  height: 1,
  margin: -1,
  padding: 0,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
  border: 0,
};

const inputSx = { '& .MuiOutlinedInput-root': { minHeight: tokens.controlHeight.standard } };

const DEFAULT_VALUES = {
  businessName: '',
  businessRegistrationNo: '',
  contactName: '',
  email: '',
  phone: '',
  password: '',
  addressLine1: '',
  city: '',
};

const NETWORK_ERROR = 'The server could not be reached. Please check your connection and try again.';

function LabeledField({ id, label, optional = false, children }) {
  return (
    <Box>
      <Typography
        component="label"
        htmlFor={id}
        sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.5, color: tokens.palette.text }}
      >
        {label}
        {optional && (
          <Typography component="span" sx={{ fontWeight: 400, color: tokens.palette.mutedText, ml: 0.5 }}>
            (optional)
          </Typography>
        )}
      </Typography>
      {children}
    </Box>
  );
}

function PasswordRules({ password, email }) {
  const rules = passwordRules(password, email);
  return (
    <Box
      component="ul"
      aria-label="Password requirements"
      sx={{ listStyle: 'none', m: 0, mt: 1, p: 0, display: 'grid', gap: 0.25 }}
    >
      {rules.map((rule) => {
        const Icon = rule.met ? CheckCircleOutlineIcon : RadioButtonUncheckedIcon;
        return (
          <Box
            component="li"
            key={rule.id}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.75,
              fontSize: '0.8125rem',
              color: rule.met ? tokens.status.DELIVERED.text : tokens.palette.mutedText,
            }}
          >
            <Icon sx={{ fontSize: '1rem' }} aria-hidden="true" />
            <span style={visuallyHidden}>{rule.met ? 'Met: ' : 'Not met: '}</span>
            <span>{rule.label}</span>
          </Box>
        );
      })}
    </Box>
  );
}

function PendingApproval() {
  const palette = tokens.status.DELIVERED;
  return (
    <Box
      role="status"
      sx={{
        p: 3,
        borderRadius: tokens.radius.control,
        backgroundColor: palette.bg,
        border: `1px solid ${tokens.palette.border}`,
        textAlign: 'center',
      }}
    >
      <HourglassTopOutlinedIcon sx={{ fontSize: 44, color: palette.text, mb: 1 }} aria-hidden="true" />
      <Typography variant="h6" component="h2" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 1 }}>
        Your registration is waiting for admin approval
      </Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 3 }}>
        We have received your business details. You will be able to log in and create API credentials once an
        administrator approves your account.
      </Typography>
      <Button
        component={RouterLink}
        to="/login"
        variant="contained"
        sx={{
          backgroundColor: tokens.palette.primary,
          height: tokens.controlHeight.standard,
          px: 4,
          fontWeight: 600,
          '&:hover': { backgroundColor: tokens.palette.primaryHover },
        }}
      >
        Go to login
      </Button>
    </Box>
  );
}

export default function RegisterMerchantPage() {
  const [showPassword, setShowPassword] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(registerMerchantSchema),
    defaultValues: DEFAULT_VALUES,
    mode: 'onSubmit',
  });

  const password = watch('password', '');
  const email = watch('email', '');

  const onSubmit = async (values) => {
    try {
      await registerMerchant(toRequestBody(values));
      reset(DEFAULT_VALUES); // the password never stays in the form after a successful registration
      setSubmitted(true);
    } catch (err) {
      if (err?.code === 'EMAIL_ALREADY_REGISTERED') {
        setError('email', { type: 'server', message: 'An account with this email address already exists. Try logging in.' });
      } else {
        // field errors from errors[] are shown next to their fields; anything else goes in the alert. Input is kept.
        applyServerErrors(setError, err?.status ? err : { detail: NETWORK_ERROR });
      }
    }
  };

  const fieldProps = (name, extra = {}) => ({
    id: name,
    size: 'small',
    fullWidth: true,
    disabled: isSubmitting,
    error: Boolean(errors[name]),
    helperText: errors[name]?.message,
    sx: inputSx,
    ...register(name),
    ...extra,
  });

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', py: { xs: 2, sm: 4 }, px: 2 }}>
      <Card
        sx={{
          width: '100%',
          maxWidth: 760,
          p: { xs: 2.5, sm: 4 },
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          backgroundColor: tokens.palette.surface,
        }}
      >
        <Typography variant="h5" component="h1" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 0.5 }}>
          Register your business
        </Typography>
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 3 }}>
          Create a merchant account to send parcels through the SwiftDrop API. An administrator reviews every
          registration before you can log in.
        </Typography>

        {submitted ? (
          <PendingApproval />
        ) : (
          <>
            {errors.root?.serverError && (
              <Alert severity="error" sx={{ mb: 2.5, borderRadius: tokens.radius.control }}>
                {errors.root.serverError.message}
              </Alert>
            )}
            <Box
              component="form"
              onSubmit={handleSubmit(onSubmit)}
              noValidate
              aria-label="Merchant registration"
              sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}
            >
              <LabeledField id="businessName" label="Business name">
                <TextField {...fieldProps('businessName', { autoComplete: 'organization' })} />
              </LabeledField>

              <LabeledField id="businessRegistrationNo" label="Business registration number" optional>
                <TextField {...fieldProps('businessRegistrationNo')} />
              </LabeledField>

              <LabeledField id="contactName" label="Contact person">
                <TextField {...fieldProps('contactName', { autoComplete: 'name' })} />
              </LabeledField>

              <LabeledField id="phone" label="Phone number">
                <TextField {...fieldProps('phone', { type: 'tel', autoComplete: 'tel', placeholder: '0771234567' })} />
              </LabeledField>

              <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
                <LabeledField id="email" label="Email address">
                  <TextField {...fieldProps('email', { type: 'email', autoComplete: 'email' })} />
                </LabeledField>
              </Box>

              <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
                <LabeledField id="password" label="Password">
                  <TextField
                    {...fieldProps('password', {
                      type: showPassword ? 'text' : 'password',
                      autoComplete: 'new-password',
                      InputProps: {
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton
                              aria-label={showPassword ? 'Hide password' : 'Show password'}
                              onClick={() => setShowPassword((value) => !value)}
                              edge="end"
                              size="small"
                            >
                              {showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}
                            </IconButton>
                          </InputAdornment>
                        ),
                      },
                    })}
                  />
                </LabeledField>
                <PasswordRules password={password} email={email} />
              </Box>

              <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
                <LabeledField id="addressLine1" label="Business address">
                  <TextField {...fieldProps('addressLine1', { autoComplete: 'street-address' })} />
                </LabeledField>
              </Box>

              <LabeledField id="city" label="City">
                <TextField {...fieldProps('city', { autoComplete: 'address-level2' })} />
              </LabeledField>

              <Box sx={{ gridColumn: { sm: '1 / -1' }, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 2, mt: 1 }}>
                <Button
                  type="submit"
                  variant="contained"
                  disabled={isSubmitting}
                  sx={{
                    backgroundColor: tokens.palette.primary,
                    height: tokens.controlHeight.standard,
                    px: 4,
                    fontWeight: 600,
                    '&:hover': { backgroundColor: tokens.palette.primaryHover },
                  }}
                >
                  {isSubmitting ? 'Registering...' : 'Register business'}
                </Button>
                <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                  Already registered?{' '}
                  <Typography component={RouterLink} to="/login" variant="body2" sx={{ color: tokens.palette.primaryPressed, fontWeight: 600 }}>
                    Log in
                  </Typography>
                </Typography>
              </Box>
            </Box>
          </>
        )}
      </Card>
    </Box>
  );
}
