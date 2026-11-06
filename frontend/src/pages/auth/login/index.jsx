import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
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
import { tokens } from '../../../theme/theme';
import { useAuth } from '../../../context/AuthContext';
import GoogleLoginButton from '../../../components/GoogleLoginButton';
import logoImg from '../../../assets/logo.png';
import heroCourierImg from '../../../assets/hero-courier.jpg';
import { ROLE_HOMES } from '../../../routes/routeTable';

const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Email is required')
    .max(254, 'Email is too long')
    .email('Please enter a valid email address'),
  password: z
    .string()
    .min(1, 'Password is required'),
});

const OAUTH_ERROR_MESSAGES = {
  google_denied: 'Google sign-in was cancelled. Please try again or use your password.',
  google_failed: 'Google sign-in could not be completed. Please try again.',
  account_exists: 'An account with this email already exists. Please log in with your email and password.',
  account_deactivated: 'Your account is deactivated. Please contact customer support.',
};

export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const oauthErrorKey = searchParams.get('error');
  const oauthErrorMessage = oauthErrorKey ? OAUTH_ERROR_MESSAGES[oauthErrorKey] || 'Authentication error. Please try again.' : null;

  const [showPassword, setShowPassword] = useState(false);
  const [serverError, setServerError] = useState(oauthErrorMessage);
  const [retryAfterSeconds, setRetryAfterSeconds] = useState(null);

  const { login } = useAuth();
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = async (data) => {
    setServerError(null);
    setRetryAfterSeconds(null);

    try {
      const result = await login(data);
      const user = result?.user;

      if (user?.mustChangePassword) {
        navigate('/change-password');
      } else if (user?.role && ROLE_HOMES[user.role]) {
        navigate(ROLE_HOMES[user.role]);
      } else {
        navigate('/customer/dashboard');
      }
    } catch (err) {
      if (err.status === 429 || err.code === 'ACCOUNT_LOCKED') {
        const wait = err.retryAfter || 900;
        const minutes = Math.ceil(wait / 60);
        setRetryAfterSeconds(wait);
        setServerError(`Account locked due to 5 consecutive failed attempts. Please wait ${minutes} minute(s) before trying again.`);
      } else if (err.code === 'INVALID_CREDENTIALS' || err.status === 401) {
        setServerError('Invalid email or password');
      } else if (err.code === 'ACCOUNT_DEACTIVATED') {
        setServerError('Your account has been deactivated. Please contact support.');
      } else if (err.code === 'MERCHANT_PENDING') {
        setServerError('Your merchant account is currently pending review by our administration team.');
      } else if (err.code === 'MERCHANT_REJECTED') {
        setServerError('Your merchant application has not been approved.');
      } else if (err.code === 'MERCHANT_SUSPENDED') {
        setServerError('Your merchant account is currently suspended.');
      } else {
        setServerError(err.detail || 'Login failed. Please check your credentials.');
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
        {/* Left column: Login Form */}
        <Box sx={{ p: { xs: 3, sm: 4, md: 5 }, display: 'flex', flexDirection: 'column' }}>
          {/* Logo & Tagline */}
          <Box sx={{ mb: 3 }}>
            <Box component="img" src={logoImg} alt="SwiftDrop" sx={{ height: 34, width: 'auto', mb: 0.8 }} />
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText, fontWeight: 500 }}>
              Deliver smarter. Live better.
            </Typography>
          </Box>

          <Box sx={{ mb: 3 }}>
            <Typography variant="h5" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 0.5 }}>
              Login
            </Typography>
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
              Enter your credentials to access your SwiftDrop account
            </Typography>
          </Box>

          {/* Error Banner */}
          {serverError && (
            <Alert severity="error" sx={{ mb: 3, borderRadius: tokens.radius.control }}>
              {serverError}
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.2 }}>
            <Box>
              <Typography
                component="label"
                htmlFor="email"
                sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.8, color: tokens.palette.text }}
              >
                Email Address
              </Typography>
              <TextField
                id="email"
                type="email"
                fullWidth
                placeholder="e.g. kasun.perera@example.lk"
                autoComplete="email"
                disabled={isSubmitting || Boolean(retryAfterSeconds)}
                error={Boolean(errors.email)}
                helperText={errors.email?.message}
                {...register('email')}
              />
            </Box>

            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 0.8 }}>
                <Typography
                  component="label"
                  htmlFor="password"
                  sx={{ fontWeight: 600, fontSize: '0.875rem', color: tokens.palette.text }}
                >
                  Password
                </Typography>
              </Box>
              <TextField
                id="password"
                type={showPassword ? 'text' : 'password'}
                fullWidth
                placeholder="••••••••••••"
                autoComplete="current-password"
                disabled={isSubmitting || Boolean(retryAfterSeconds)}
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
            </Box>

            <Button
              type="submit"
              variant="contained"
              fullWidth
              disabled={isSubmitting || Boolean(retryAfterSeconds)}
              sx={{
                height: tokens.controlHeight.standard,
                backgroundColor: tokens.palette.primary,
                fontWeight: 600,
                fontSize: '0.9375rem',
                mt: 1,
                '&:hover': { backgroundColor: tokens.palette.primaryHover },
              }}
            >
              {isSubmitting ? 'Logging in...' : 'Login'}
            </Button>
          </Box>

          <Divider sx={{ my: 3 }}>
            <Typography variant="caption" sx={{ color: tokens.palette.mutedText, px: 1 }}>
              or continue with
            </Typography>
          </Divider>

          <GoogleLoginButton disabled={isSubmitting} text="Continue with Google" />

          {/* Links */}
          <Box sx={{ mt: 3, textAlign: 'center' }}>
            <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
              Don't have an account?{' '}
              <Typography
                component={Link}
                to="/register"
                sx={{
                  color: tokens.palette.primary,
                  fontWeight: 600,
                  textDecoration: 'none',
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                Sign Up
              </Typography>
            </Typography>
          </Box>
        </Box>

        {/* Right column: Smoothly framed hero card */}
        <Box
          sx={{
            display: { xs: 'none', md: 'flex' },
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            p: { md: 3, lg: 4 },
            backgroundColor: '#FAFCFF',
          }}
        >
          <Box
            sx={{
              width: '100%',
              maxWidth: 420,
              height: '100%',
              maxHeight: 520,
              minHeight: 380,
              borderRadius: '20px',
              overflow: 'hidden',
              position: 'relative',
              boxShadow: '0 10px 28px rgba(15, 23, 42, 0.08)',
              border: `1px solid ${tokens.palette.border}`,
              backgroundImage: `url(${heroCourierImg})`,
              backgroundSize: 'cover',
              backgroundPosition: 'center 20%',
              backgroundRepeat: 'no-repeat',
            }}
          />
        </Box>
      </Card>
    </Box>
  );
}

