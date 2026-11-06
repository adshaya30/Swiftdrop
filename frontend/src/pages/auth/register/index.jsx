import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { tokens } from '../../../theme/theme';
import * as authApi from '../../../api/auth';
import GoogleLoginButton from '../../../components/GoogleLoginButton';
import logoImg from '../../../assets/logo.png';
import heroCourierImg from '../../../assets/hero-courier.jpg';
import { applyServerErrors } from '../../../utils/format';

// Password policy regex checks
const hasUpper = (p) => /[A-Z]/.test(p);
const hasLower = (p) => /[a-z]/.test(p);
const hasDigit = (p) => /[0-9]/.test(p);
const hasSpecial = (p) => /[^a-zA-Z0-9]/.test(p);

const registerSchema = z
  .object({
    fullName: z
      .string()
      .min(2, 'Full name must be at least 2 characters')
      .max(100, 'Full name cannot exceed 100 characters'),
    email: z
      .string()
      .min(1, 'Email is required')
      .max(254, 'Email is too long')
      .email('Please enter a valid email address'),
    phone: z
      .string()
      .min(9, 'Phone number is too short')
      .max(15, 'Phone number is too long')
      .regex(
        /^(?:0|94|\+94)?(?:7[0-9]|1[1-9]|2[1-8]|3[1-8]|4[1-7]|5[1-7]|6[3-7]|8[1-7]|9[1-2])[0-9]{7}$/,
        'Please enter a valid Sri Lankan phone number (e.g., 0771234567 or +94771234567)'
      ),
    password: z
      .string()
      .min(10, 'Password must be at least 10 characters')
      .max(64, 'Password cannot exceed 64 characters')
      .refine(hasUpper, 'Must contain at least one uppercase letter')
      .refine(hasLower, 'Must contain at least one lowercase letter')
      .refine(hasDigit, 'Must contain at least one digit')
      .refine(hasSpecial, 'Must contain at least one special character'),
  })
  .refine(
    (data) => {
      if (!data.email || !data.email.includes('@')) return true;
      const localPart = data.email.split('@')[0].trim().toLowerCase();
      if (!localPart) return true;
      return !data.password.toLowerCase().includes(localPart);
    },
    {
      message: 'Password must not contain your email local part',
      path: ['password'],
    }
  );

export default function RegisterPage() {
  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState(null);
  const [success, setSuccess] = useState(false);
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      fullName: '',
      email: '',
      phone: '',
      password: '',
    },
  });

  const enteredPassword = watch('password', '');

  const onSubmit = async (data) => {
    setServerError(null);

    try {
      await authApi.register(data);
      setSuccess(true);
    } catch (err) {
      if (err.code === 'EMAIL_ALREADY_REGISTERED' || err.status === 409) {
        setServerError('An account with this email address is already registered. Please log in.');
      } else {
        applyServerErrors(setError, err);
        setServerError(err.detail || 'Registration failed. Please check your inputs.');
      }
    }
  };

  return (
    <Box
      sx={{
        minHeight: 'calc(100vh - 140px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: { xs: 2, sm: 3, md: 5 },
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 960,
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: '1.1fr 1fr' },
          overflow: 'hidden',
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          boxShadow: '0 10px 30px rgba(15, 23, 42, 0.06)',
          backgroundColor: tokens.palette.surface,
        }}
      >
        {/* Left Column: Form */}
        <Box sx={{ p: { xs: 3, sm: 4, md: 5 }, display: 'flex', flexDirection: 'column' }}>
          {/* Logo & Tagline */}
          <Box sx={{ mb: 2.5 }}>
            <Box component="img" src={logoImg} alt="SwiftDrop" sx={{ height: 34, width: 'auto', mb: 0.8 }} />
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText, fontWeight: 500 }}>
              Deliver smarter. Live better.
            </Typography>
          </Box>

          <Box sx={{ mb: 2.5 }}>
            <Typography variant="h5" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 0.5 }}>
              Create Account
            </Typography>
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
              Sign up to ship, manage, and track parcels across Sri Lanka
            </Typography>
          </Box>

          {success ? (
            <Box
              sx={{
                p: 3,
                borderRadius: tokens.radius.control,
                backgroundColor: '#F0FDF4',
                border: '1px solid #BBF7D0',
                textAlign: 'center',
                my: 2,
              }}
            >
              <CheckCircleOutlineIcon sx={{ fontSize: 48, color: '#16A34A', mb: 1 }} />
              <Typography variant="h6" sx={{ fontWeight: 700, color: '#166534', mb: 1 }}>
                Registration Successful!
              </Typography>
              <Typography variant="body2" sx={{ color: '#15803D', mb: 3 }}>
                Your SwiftDrop account has been created. You can now log in to access your dashboard.
              </Typography>
              <Button
                variant="contained"
                onClick={() => navigate('/login')}
                sx={{
                  backgroundColor: tokens.palette.primary,
                  height: tokens.controlHeight.standard,
                  px: 4,
                  fontWeight: 600,
                  '&:hover': { backgroundColor: tokens.palette.primaryHover },
                }}
              >
                Proceed to Login
              </Button>
            </Box>
          ) : (
            <>
              {serverError && (
                <Alert severity="error" sx={{ mb: 2.5, borderRadius: tokens.radius.control }}>
                  {serverError}
                </Alert>
              )}

              <Box
                component="form"
                onSubmit={handleSubmit(onSubmit)}
                noValidate
                sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
              >
                <Box>
                  <Typography
                    component="label"
                    htmlFor="fullName"
                    sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6, color: tokens.palette.text }}
                  >
                    Full Name
                  </Typography>
                  <TextField
                    id="fullName"
                    fullWidth
                    placeholder="e.g. Kasun Perera"
                    autoComplete="name"
                    disabled={isSubmitting}
                    error={Boolean(errors.fullName)}
                    helperText={errors.fullName?.message}
                    {...register('fullName')}
                  />
                </Box>

                <Box>
                  <Typography
                    component="label"
                    htmlFor="email"
                    sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6, color: tokens.palette.text }}
                  >
                    Email Address
                  </Typography>
                  <TextField
                    id="email"
                    type="email"
                    fullWidth
                    placeholder="e.g. kasun.perera@example.lk"
                    autoComplete="email"
                    disabled={isSubmitting}
                    error={Boolean(errors.email)}
                    helperText={errors.email?.message}
                    {...register('email')}
                  />
                </Box>

                <Box>
                  <Typography
                    component="label"
                    htmlFor="phone"
                    sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6, color: tokens.palette.text }}
                  >
                    Phone Number
                  </Typography>
                  <TextField
                    id="phone"
                    type="tel"
                    fullWidth
                    placeholder="e.g. 0771234567"
                    autoComplete="tel"
                    disabled={isSubmitting}
                    error={Boolean(errors.phone)}
                    helperText={errors.phone?.message}
                    {...register('phone')}
                  />
                </Box>

                <Box>
                  <Typography
                    component="label"
                    htmlFor="password"
                    sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6, color: tokens.palette.text }}
                  >
                    Password
                  </Typography>
                  <TextField
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    fullWidth
                    placeholder="At least 10 characters with upper, lower, digit & special"
                    autoComplete="new-password"
                    disabled={isSubmitting}
                    error={Boolean(errors.password)}
                    helperText={errors.password?.message}
                    {...register('password')}
                    InputProps={{
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            onClick={() => setShowPassword(!showPassword)}
                            edge="end"
                            aria-label="toggle password visibility"
                            size="small"
                          >
                            {showPassword ? <VisibilityOffOutlinedIcon fontSize="small" /> : <VisibilityOutlinedIcon fontSize="small" />}
                          </IconButton>
                        </InputAdornment>
                      ),
                    }}
                  />

                  {/* Password requirements visual hints */}
                  <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 0.8 }}>
                    <PolicyHint label="10-64 chars" met={enteredPassword.length >= 10 && enteredPassword.length <= 64} />
                    <PolicyHint label="Uppercase" met={hasUpper(enteredPassword)} />
                    <PolicyHint label="Lowercase" met={hasLower(enteredPassword)} />
                    <PolicyHint label="Number" met={hasDigit(enteredPassword)} />
                    <PolicyHint label="Special" met={hasSpecial(enteredPassword)} />
                  </Box>
                </Box>

                <Button
                  type="submit"
                  variant="contained"
                  fullWidth
                  disabled={isSubmitting}
                  sx={{
                    height: tokens.controlHeight.standard,
                    backgroundColor: tokens.palette.primary,
                    fontWeight: 600,
                    fontSize: '0.9375rem',
                    mt: 1,
                    '&:hover': { backgroundColor: tokens.palette.primaryHover },
                  }}
                >
                  {isSubmitting ? 'Creating Account...' : 'Create Account'}
                </Button>
              </Box>

              <Divider sx={{ my: 2.5 }}>
                <Typography variant="caption" sx={{ color: tokens.palette.mutedText, px: 1 }}>
                  or sign up with
                </Typography>
              </Divider>

              <GoogleLoginButton disabled={isSubmitting} text="Sign Up with Google" />

              <Box sx={{ mt: 2.5, textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 0.8 }}>
                <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                  Already have an account?{' '}
                  <Typography
                    component={Link}
                    to="/login"
                    sx={{
                      color: tokens.palette.primary,
                      fontWeight: 600,
                      textDecoration: 'none',
                      '&:hover': { textDecoration: 'underline' },
                    }}
                  >
                    Login
                  </Typography>
                </Typography>
                <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                  Registering as a business?{' '}
                  <Typography
                    component={Link}
                    to="/register-merchant"
                    sx={{
                      color: tokens.palette.navy,
                      fontWeight: 600,
                      textDecoration: 'none',
                      '&:hover': { textDecoration: 'underline' },
                    }}
                  >
                    Register Merchant Account
                  </Typography>
                </Typography>
              </Box>
            </>
          )}
        </Box>

        {/* Right Column: Hero image */}
        <Box
          sx={{
            display: { xs: 'none', md: 'flex' },
            position: 'relative',
            backgroundColor: tokens.palette.softOrange,
            backgroundImage: `url(${heroCourierImg})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            minHeight: '100%',
          }}
        />
      </Card>
    </Box>
  );
}

function PolicyHint({ label, met }) {
  return (
    <Typography
      variant="caption"
      sx={{
        px: 1,
        py: 0.2,
        borderRadius: '12px',
        fontSize: '0.72rem',
        fontWeight: 600,
        backgroundColor: met ? '#DCFCE7' : '#F1F5F9',
        color: met ? '#15803D' : '#64748B',
        transition: 'all 150ms ease',
      }}
    >
      {met ? '✓ ' : '• '}
      {label}
    </Typography>
  );
}
