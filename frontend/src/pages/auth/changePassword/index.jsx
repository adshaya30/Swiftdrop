import React, { useState, useMemo, useEffect } from 'react';
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
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import { useAuth } from '../../../context/AuthContext';
import * as profileApi from '../../../api/profile';
import { tokens } from '../../../theme/theme';

export default function ChangePasswordPage() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [userEmail, setUserEmail] = useState(user?.email || '');

  useEffect(() => {
    profileApi.getMe()
      .then((data) => {
        if (data && data.email) {
          setUserEmail(data.email);
        }
      })
      .catch(() => {});
  }, []);

  const isForced = Boolean(user?.mustChangePassword);

  const emailLocalPart = useMemo(() => {
    if (!userEmail || !userEmail.includes('@')) return '';
    return userEmail.split('@')[0].toLowerCase();
  }, [userEmail]);

  const checks = useMemo(() => {
    return {
      length: newPassword.length >= 10 && newPassword.length <= 64,
      upper: /[A-Z]/.test(newPassword),
      lower: /[a-z]/.test(newPassword),
      digit: /[0-9]/.test(newPassword),
      special: /[^a-zA-Z0-9]/.test(newPassword),
      differs: Boolean(newPassword && currentPassword && newPassword !== currentPassword),
      noLocal: Boolean(!emailLocalPart || !newPassword.toLowerCase().includes(emailLocalPart)),
      matches: Boolean(newPassword && newPassword === confirmPassword),
    };
  }, [newPassword, currentPassword, confirmPassword, emailLocalPart]);

  const isValid =
    Boolean(currentPassword.trim()) &&
    checks.length &&
    checks.upper &&
    checks.lower &&
    checks.digit &&
    checks.special &&
    checks.differs &&
    checks.noLocal &&
    checks.matches;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setServerError(null);
    setSuccessMessage(null);

    if (!isValid) {
      setServerError('Please ensure all password policy requirements are met.');
      return;
    }

    setIsSubmitting(true);
    try {
      await profileApi.changePassword({
        currentPassword,
        newPassword,
      });

      setSuccessMessage('Password changed successfully! You will now be redirected to sign in with your new password.');
      await logout();

      setTimeout(() => {
        navigate('/login', { replace: true });
      }, 1500);
    } catch (err) {
      setServerError(err.detail || 'Failed to change password. Please verify your current password.');
      setIsSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: 'calc(100vh - 160px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        p: { xs: 2, sm: 3, md: 4 },
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 540,
          p: { xs: 3, sm: 4 },
          borderRadius: tokens.radius.card,
          border: `1px solid ${tokens.palette.border}`,
          boxShadow: '0 8px 30px rgba(15, 23, 42, 0.06)',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1 }}>
          <LockOutlinedIcon sx={{ color: tokens.palette.primary, fontSize: 30 }} />
          <Typography variant="h5" sx={{ fontWeight: 700, color: tokens.palette.text }}>
            {isForced ? 'Set New Password' : 'Change Password'}
          </Typography>
        </Box>

        <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 2.5 }}>
          {isForced
            ? 'Your account requires a password change before continuing. Please choose a strong password.'
            : 'Enter your current password and choose a new secure password.'}
        </Typography>

        {serverError && (
          <Alert severity="error" sx={{ mb: 2.5, borderRadius: tokens.radius.control }}>
            {serverError}
          </Alert>
        )}

        {successMessage && (
          <Alert severity="success" sx={{ mb: 2.5, borderRadius: tokens.radius.control }}>
            {successMessage}
          </Alert>
        )}

        <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.2 }}>
          {/* Current Password */}
          <Box>
            <Typography component="label" htmlFor="currentPassword" sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6 }}>
              Current Password
            </Typography>
            <TextField
              id="currentPassword"
              type={showCurrent ? 'text' : 'password'}
              fullWidth
              placeholder="Enter current password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              disabled={isSubmitting}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setShowCurrent(!showCurrent)} edge="end" aria-label="toggle current password visibility">
                      {showCurrent ? <VisibilityOffOutlinedIcon fontSize="small" /> : <VisibilityOutlinedIcon fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          {/* New Password */}
          <Box>
            <Typography component="label" htmlFor="newPassword" sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6 }}>
              New Password
            </Typography>
            <TextField
              id="newPassword"
              type={showNew ? 'text' : 'password'}
              fullWidth
              placeholder="At least 10 characters with upper, lower, digit & special"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              disabled={isSubmitting}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setShowNew(!showNew)} edge="end" aria-label="toggle new password visibility">
                      {showNew ? <VisibilityOffOutlinedIcon fontSize="small" /> : <VisibilityOutlinedIcon fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          {/* Confirm New Password */}
          <Box>
            <Typography component="label" htmlFor="confirmPassword" sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.6 }}>
              Confirm New Password
            </Typography>
            <TextField
              id="confirmPassword"
              type={showConfirm ? 'text' : 'password'}
              fullWidth
              placeholder="Re-enter new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={isSubmitting}
              InputProps={{
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={() => setShowConfirm(!showConfirm)} edge="end" aria-label="toggle confirm password visibility">
                      {showConfirm ? <VisibilityOffOutlinedIcon fontSize="small" /> : <VisibilityOutlinedIcon fontSize="small" />}
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
          </Box>

          {/* Live Password Policy Checklist */}
          <Box
            sx={{
              p: 2,
              borderRadius: tokens.radius.control,
              backgroundColor: '#F8FAFC',
              border: `1px solid ${tokens.palette.border}`,
            }}
          >
            <Typography variant="caption" sx={{ fontWeight: 700, color: tokens.palette.navy, display: 'block', mb: 1 }}>
              Password Requirements Checklist:
            </Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 0.8 }}>
              <PolicyCheck label="10 to 64 characters" met={checks.length} />
              <PolicyCheck label="At least 1 uppercase (A-Z)" met={checks.upper} />
              <PolicyCheck label="At least 1 lowercase (a-z)" met={checks.lower} />
              <PolicyCheck label="At least 1 number (0-9)" met={checks.digit} />
              <PolicyCheck label="At least 1 special character" met={checks.special} />
              <PolicyCheck label="Different from current" met={checks.differs} />
              <PolicyCheck label="No email address in password" met={checks.noLocal} />
              <PolicyCheck label="Passwords match" met={checks.matches} />
            </Box>
          </Box>

          <Button
            type="submit"
            variant="contained"
            fullWidth
            disabled={!isValid || isSubmitting}
            sx={{
              height: tokens.controlHeight.standard,
              backgroundColor: tokens.palette.primary,
              fontWeight: 600,
              mt: 1,
              '&:hover': { backgroundColor: tokens.palette.primaryHover },
            }}
          >
            {isSubmitting ? 'Updating Password...' : 'Update Password'}
          </Button>

          {!isForced && (
            <Box sx={{ textAlign: 'center', mt: 1 }}>
              <Typography
                component={Link}
                to="/profile"
                sx={{
                  color: tokens.palette.mutedText,
                  textDecoration: 'none',
                  fontSize: '0.875rem',
                  fontWeight: 500,
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                Back to Profile
              </Typography>
            </Box>
          )}
        </Box>
      </Card>
    </Box>
  );
}

function PolicyCheck({ label, met }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8 }}>
      {met ? (
        <CheckCircleOutlineIcon sx={{ color: tokens.palette.success, fontSize: 16 }} />
      ) : (
        <CancelOutlinedIcon sx={{ color: '#94A3B8', fontSize: 16 }} />
      )}
      <Typography
        variant="caption"
        sx={{
          color: met ? '#15803D' : tokens.palette.mutedText,
          fontWeight: met ? 600 : 400,
          fontSize: '0.75rem',
        }}
      >
        {label}
      </Typography>
    </Box>
  );
}

