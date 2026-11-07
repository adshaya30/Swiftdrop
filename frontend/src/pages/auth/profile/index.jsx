import React, { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import CircularProgress from '@mui/material/CircularProgress';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import CheckOutlinedIcon from '@mui/icons-material/CheckOutlined';
import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import * as profileApi from '../../../api/profile';
import { tokens } from '../../../theme/theme';
import { formatDateTime } from '../../../utils/format';

export default function ProfilePage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({ fullName: '', phone: '' });
  const [errors, setErrors] = useState({});
  const [serverError, setServerError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  const fetchProfile = async () => {
    setLoading(true);
    setServerError(null);
    try {
      const data = await profileApi.getMe();
      setProfile(data);
      setFormData({
        fullName: data.fullName || '',
        phone: data.phone || '',
      });
    } catch (err) {
      setServerError(err.detail || 'Failed to load profile.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const validate = () => {
    const errs = {};
    if (!formData.fullName.trim()) {
      errs.fullName = 'Full name is required';
    } else if (formData.fullName.trim().length < 2 || formData.fullName.trim().length > 100) {
      errs.fullName = 'Full name must be between 2 and 100 characters';
    }

    if (formData.phone && formData.phone.trim()) {
      const phoneRegex = /^(?:0|94|\+94)?(?:7[0-9]|1[1-9]|2[1-8]|3[1-8]|4[1-7]|5[1-7]|6[3-7]|8[1-7]|9[1-2])[0-9]{7}$/;
      if (!phoneRegex.test(formData.phone.trim())) {
        errs.phone = 'Please enter a valid phone number (e.g. 0771234567 or +94771234567)';
      }
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setServerError(null);
    setSuccessMessage(null);

    if (!validate()) return;

    setSaving(true);
    try {
      const updated = await profileApi.updateMe({
        fullName: formData.fullName.trim(),
        phone: formData.phone.trim() || null,
      });
      setProfile(updated);
      setIsEditing(false);
      setSuccessMessage('Profile updated successfully.');
    } catch (err) {
      if (err.errors && Array.isArray(err.errors)) {
        const fieldErrors = {};
        err.errors.forEach((e) => {
          fieldErrors[e.field] = e.message;
        });
        setErrors(fieldErrors);
      }
      setServerError(err.detail || 'Failed to update profile.');
    } finally {
      setSaving(false);
    }
  };

  const handleCancel = () => {
    setIsEditing(false);
    setErrors({});
    if (profile) {
      setFormData({
        fullName: profile.fullName || '',
        phone: profile.phone || '',
      });
    }
  };

  if (loading) {
    return (
      <Box sx={{ minHeight: '50vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <CircularProgress size={40} sx={{ color: tokens.palette.primary }} />
      </Box>
    );
  }

  const isGoogleAccount = profile?.authProvider === 'GOOGLE';

  return (
    <Box sx={{ p: { xs: 2, sm: 3, md: 4 }, maxWidth: 860, mx: 'auto' }}>
      {/* Page Title */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 0.5 }}>
          My Profile
        </Typography>
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
          Manage your account details and security settings
        </Typography>
      </Box>

      {serverError && (
        <Alert severity="error" sx={{ mb: 3, borderRadius: tokens.radius.control }}>
          {serverError}
        </Alert>
      )}

      {successMessage && (
        <Alert severity="success" sx={{ mb: 3, borderRadius: tokens.radius.control }}>
          {successMessage}
        </Alert>
      )}

      {profile && (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
          {/* Main Profile Info Card */}
          <Card
            sx={{
              p: { xs: 2.5, sm: 3.5 },
              borderRadius: tokens.radius.card,
              border: `1px solid ${tokens.palette.border}`,
              boxShadow: '0 4px 20px rgba(15, 23, 42, 0.04)',
            }}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                <PersonOutlineIcon sx={{ color: tokens.palette.primary, fontSize: 28 }} />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>
                  Personal Information
                </Typography>
              </Box>

              {!isEditing && (
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<EditOutlinedIcon />}
                  onClick={() => setIsEditing(true)}
                  sx={{
                    textTransform: 'none',
                    fontWeight: 600,
                    borderRadius: tokens.radius.control,
                  }}
                >
                  Edit Profile
                </Button>
              )}
            </Box>

            <Divider sx={{ mb: 3 }} />

            {isEditing ? (
              <Box component="form" onSubmit={handleSave} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>
                <Box>
                  <Typography component="label" sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.8 }}>
                    Full Name
                  </Typography>
                  <TextField
                    fullWidth
                    value={formData.fullName}
                    onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                    error={Boolean(errors.fullName)}
                    helperText={errors.fullName}
                    disabled={saving}
                  />
                </Box>

                <Box>
                  <Typography component="label" sx={{ display: 'block', fontWeight: 600, fontSize: '0.875rem', mb: 0.8 }}>
                    Phone Number
                  </Typography>
                  <TextField
                    fullWidth
                    placeholder="e.g. 0771234567"
                    value={formData.phone}
                    onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                    error={Boolean(errors.phone)}
                    helperText={errors.phone}
                    disabled={saving}
                  />
                </Box>

                <Box sx={{ display: 'flex', gap: 2, mt: 1 }}>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={saving}
                    startIcon={<CheckOutlinedIcon />}
                    sx={{
                      backgroundColor: tokens.palette.primary,
                      fontWeight: 600,
                      '&:hover': { backgroundColor: tokens.palette.primaryHover },
                    }}
                  >
                    {saving ? 'Saving...' : 'Save Changes'}
                  </Button>
                  <Button
                    variant="outlined"
                    disabled={saving}
                    startIcon={<CloseOutlinedIcon />}
                    onClick={handleCancel}
                    sx={{ color: tokens.palette.mutedText }}
                  >
                    Cancel
                  </Button>
                </Box>
              </Box>
            ) : (
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2.5 }}>
                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                    Full Name
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {profile.fullName}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                    Email Address
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {profile.email}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                    Phone Number
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {profile.phone || 'Not provided'}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                    Role & Status
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, mt: 0.5 }}>
                    <Chip label={profile.role} size="small" color="primary" sx={{ fontWeight: 600 }} />
                    <Chip
                      label={isGoogleAccount ? 'Google Auth' : 'Password'}
                      size="small"
                      variant="outlined"
                      sx={{ fontWeight: 500 }}
                    />
                  </Box>
                </Box>

                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                    Member Since
                  </Typography>
                  <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                    {profile.createdAt ? formatDateTime(profile.createdAt) : 'N/A'}
                  </Typography>
                </Box>
              </Box>
            )}
          </Card>

          {/* Merchant Block (if merchant) */}
          {profile.merchant && (
            <Card
              sx={{
                p: { xs: 2.5, sm: 3.5 },
                borderRadius: tokens.radius.card,
                border: `1px solid ${tokens.palette.border}`,
              }}
            >
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Merchant Details
              </Typography>
              <Divider sx={{ mb: 2.5 }} />
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                    Business Name
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {profile.merchant.businessName}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                    Status
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600, color: tokens.palette.success }}>
                    {profile.merchant.status}
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                    Discount Rate
                  </Typography>
                  <Typography variant="body1" sx={{ fontWeight: 600 }}>
                    {profile.merchant.discountPercent}%
                  </Typography>
                </Box>
              </Box>
            </Card>
          )}

          {/* Security & Password Section */}
          <Card
            sx={{
              p: { xs: 2.5, sm: 3.5 },
              borderRadius: tokens.radius.card,
              border: `1px solid ${tokens.palette.border}`,
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
              <LockOutlinedIcon sx={{ color: tokens.palette.navy, fontSize: 24 }} />
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Security & Password
              </Typography>
            </Box>
            <Divider sx={{ mb: 2.5 }} />

            {isGoogleAccount ? (
              <Alert severity="info" sx={{ borderRadius: tokens.radius.control }}>
                Your account is linked with Google sign-in. Password changes and resets are managed through your Google account.
              </Alert>
            ) : (
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
                  For security, choose a strong password that you do not use for any other service.
                </Typography>
                <Box>
                  <Button
                    component={Link}
                    to="/change-password"
                    variant="outlined"
                    sx={{
                      fontWeight: 600,
                      borderRadius: tokens.radius.control,
                      textTransform: 'none',
                    }}
                  >
                    Change Password
                  </Button>
                </Box>
              </Box>
            )}
          </Card>
        </Box>
      )}
    </Box>
  );
}

