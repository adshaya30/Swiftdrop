import React, { useState, useEffect, useCallback } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import CircularProgress from '@mui/material/CircularProgress';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import BlockOutlinedIcon from '@mui/icons-material/BlockOutlined';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import DoneIcon from '@mui/icons-material/Done';
import * as adminUsersApi from '../../../api/adminUsers';
import { ConfirmDialog } from '../../../components/ConfirmDialog';
import { tokens } from '../../../theme/theme';
import { formatDateTime } from '../../../utils/format';
import { useAuth } from '../../../context/AuthContext';

const VEHICLE_TYPES = ['MOTORBIKE', 'THREE_WHEELER', 'VAN'];

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();

  const [users, setUsers] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(20);
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const [loading, setLoading] = useState(false);
  const [serverError, setServerError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Dialog states
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createRole, setCreateRole] = useState('COURIER'); // 'COURIER' | 'ADMIN'
  const [createForm, setCreateForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    vehicleType: 'MOTORBIKE',
    vehicleNumber: '',
  });
  const [createErrors, setCreateErrors] = useState({});
  const [createSubmitting, setCreateSubmitting] = useState(false);

  // Edit dialog state
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState(null);
  const [editForm, setEditForm] = useState({
    fullName: '',
    phone: '',
    vehicleType: '',
    vehicleNumber: '',
  });
  const [editSubmitting, setEditSubmitting] = useState(false);

  // Temp Password Dialog
  const [tempPassword, setTempPassword] = useState(null);
  const [tempPasswordCopied, setTempPasswordCopied] = useState(false);

  // Action Confirm Dialogs
  const [confirmDialog, setConfirmDialog] = useState({
    open: false,
    title: '',
    message: '',
    confirmText: '',
    isDestructive: false,
    onConfirm: null,
    loading: false,
  });

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setServerError(null);
    try {
      const data = await adminUsersApi.getUsers({
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        q: searchQuery || undefined,
        page,
        size: rowsPerPage,
        sort: 'createdAt,desc',
      });

      setUsers(data?.content || []);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      setServerError(err.detail || 'Failed to load users.');
    } finally {
      setLoading(false);
    }
  }, [roleFilter, statusFilter, searchQuery, page, rowsPerPage]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  // Open Create Dialog
  const handleOpenCreate = (role) => {
    setCreateRole(role);
    setCreateForm({
      fullName: '',
      email: '',
      phone: '',
      vehicleType: 'MOTORBIKE',
      vehicleNumber: '',
    });
    setCreateErrors({});
    setCreateDialogOpen(true);
  };

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setCreateErrors({});

    const errs = {};
    if (!createForm.fullName.trim()) errs.fullName = 'Full name is required';
    if (!createForm.email.trim()) errs.email = 'Email is required';
    if (createRole === 'COURIER') {
      if (!createForm.vehicleNumber.trim()) errs.vehicleNumber = 'Vehicle number is required';
    }

    if (Object.keys(errs).length > 0) {
      setCreateErrors(errs);
      return;
    }

    setCreateSubmitting(true);
    try {
      const payload = {
        fullName: createForm.fullName.trim(),
        email: createForm.email.trim(),
        phone: createForm.phone.trim() || undefined,
        role: createRole,
        vehicleType: createRole === 'COURIER' ? createForm.vehicleType : undefined,
        vehicleNumber: createRole === 'COURIER' ? createForm.vehicleNumber.trim() : undefined,
      };

      const result = await adminUsersApi.createUser(payload);
      setCreateDialogOpen(false);
      setTempPassword(result.temporaryPassword);
      setTempPasswordCopied(false);
      loadUsers();
    } catch (err) {
      setServerError(err.detail || 'Failed to create user.');
    } finally {
      setCreateSubmitting(false);
    }
  };

  // Open Edit Dialog
  const handleOpenEdit = (user) => {
    setSelectedUser(user);
    setEditForm({
      fullName: user.fullName || '',
      phone: user.phone || '',
      vehicleType: user.courierProfile?.vehicleType || 'MOTORBIKE',
      vehicleNumber: user.courierProfile?.vehicleNumber || '',
    });
    setEditDialogOpen(true);
  };

  const handleEditSubmit = async (e) => {
    e.preventDefault();
    if (!selectedUser) return;

    setEditSubmitting(true);
    try {
      await adminUsersApi.updateUser(selectedUser.id, {
        fullName: editForm.fullName.trim(),
        phone: editForm.phone.trim() || null,
        vehicleType: selectedUser.role === 'COURIER' ? editForm.vehicleType : undefined,
        vehicleNumber: selectedUser.role === 'COURIER' ? editForm.vehicleNumber.trim() : undefined,
      });

      setEditDialogOpen(false);
      setSuccessMessage('User updated successfully.');
      loadUsers();
    } catch (err) {
      setServerError(err.detail || 'Failed to update user.');
    } finally {
      setEditSubmitting(false);
    }
  };

  // Deactivate handler
  const handleDeactivate = (user) => {
    if (currentUser?.id === user.id) {
      setServerError('You cannot deactivate your own administrative account.');
      return;
    }

    setConfirmDialog({
      open: true,
      title: 'Deactivate User',
      message: `Are you sure you want to deactivate ${user.fullName} (${user.email})? All active refresh sessions will be revoked.`,
      confirmText: 'Deactivate',
      isDestructive: true,
      loading: false,
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, loading: true }));
        try {
          await adminUsersApi.deactivateUser(user.id);
          setConfirmDialog((prev) => ({ ...prev, open: false, loading: false }));
          setSuccessMessage(`User ${user.fullName} deactivated successfully.`);
          loadUsers();
        } catch (err) {
          setConfirmDialog((prev) => ({ ...prev, open: false, loading: false }));
          if (err.code === 'COURIER_HAS_ACTIVE_DELIVERIES' || err.status === 409) {
            setServerError('This courier still has deliveries in progress and cannot be deactivated.');
          } else if (err.code === 'SERVICE_UNAVAILABLE' || err.status === 503) {
            setServerError("Could not check the courier's deliveries. Delivery service unavailable, please try again.");
          } else if (err.status === 403) {
            setServerError('You cannot deactivate your own administrative account.');
          } else {
            setServerError(err.detail || 'Failed to deactivate user.');
          }
        }
      },
    });
  };

  // Activate handler
  const handleActivate = (user) => {
    setConfirmDialog({
      open: true,
      title: 'Activate User',
      message: `Activate account for ${user.fullName} (${user.email})?`,
      confirmText: 'Activate',
      isDestructive: false,
      loading: false,
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, loading: true }));
        try {
          await adminUsersApi.activateUser(user.id);
          setConfirmDialog((prev) => ({ ...prev, open: false, loading: false }));
          setSuccessMessage(`User ${user.fullName} activated successfully.`);
          loadUsers();
        } catch (err) {
          setConfirmDialog((prev) => ({ ...prev, open: false, loading: false }));
          setServerError(err.detail || 'Failed to activate user.');
        }
      },
    });
  };

  // Reset Password handler
  const handleResetPassword = (user) => {
    setConfirmDialog({
      open: true,
      title: 'Reset Password',
      message: `Reset password for ${user.fullName}? A new 16-character temporary password will be generated and all active sessions will be revoked.`,
      confirmText: 'Reset Password',
      isDestructive: true,
      loading: false,
      onConfirm: async () => {
        setConfirmDialog((prev) => ({ ...prev, loading: true }));
        try {
          const res = await adminUsersApi.resetPassword(user.id);
          setConfirmDialog((prev) => ({ ...prev, open: false, loading: false }));
          setTempPassword(res.temporaryPassword);
          setTempPasswordCopied(false);
          loadUsers();
        } catch (err) {
          setConfirmDialog((prev) => ({ ...prev, open: false, loading: false }));
          setServerError(err.detail || 'Failed to reset password.');
        }
      },
    });
  };

  const handleCopyPassword = () => {
    if (tempPassword) {
      navigator.clipboard.writeText(tempPassword);
      setTempPasswordCopied(true);
    }
  };

  return (
    <Box sx={{ p: { xs: 2, sm: 3, md: 4 } }}>
      {/* Header */}
      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 2, mb: 3 }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 0.5 }}>
            User Management
          </Typography>
          <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
            View, search, create, and manage administrative users and couriers
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', gap: 1.5 }}>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => handleOpenCreate('COURIER')}
            sx={{
              backgroundColor: tokens.palette.primary,
              fontWeight: 600,
              textTransform: 'none',
              borderRadius: tokens.radius.control,
              '&:hover': { backgroundColor: tokens.palette.primaryHover },
            }}
          >
            Add Courier
          </Button>
          <Button
            variant="outlined"
            startIcon={<AddIcon />}
            onClick={() => handleOpenCreate('ADMIN')}
            sx={{
              borderColor: tokens.palette.navy,
              color: tokens.palette.navy,
              fontWeight: 600,
              textTransform: 'none',
              borderRadius: tokens.radius.control,
              '&:hover': { borderColor: tokens.palette.navyHover, backgroundColor: 'rgba(23, 32, 51, 0.04)' },
            }}
          >
            Add Admin
          </Button>
        </Box>
      </Box>

      {/* Notifications */}
      {serverError && (
        <Alert severity="error" onClose={() => setServerError(null)} sx={{ mb: 2.5, borderRadius: tokens.radius.control }}>
          {serverError}
        </Alert>
      )}

      {successMessage && (
        <Alert severity="success" onClose={() => setSuccessMessage(null)} sx={{ mb: 2.5, borderRadius: tokens.radius.control }}>
          {successMessage}
        </Alert>
      )}

      {/* Filter Toolbar */}
      <Card sx={{ p: 2, mb: 3, borderRadius: tokens.radius.card, border: `1px solid ${tokens.palette.border}` }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1fr' }, gap: 2 }}>
          <TextField
            size="small"
            placeholder="Search by name or email prefix..."
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setPage(0);
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" sx={{ color: tokens.palette.mutedText }} />
                </InputAdornment>
              ),
            }}
          />

          <FormControl size="small">
            <InputLabel id="role-filter-label">Role</InputLabel>
            <Select
              labelId="role-filter-label"
              value={roleFilter}
              label="Role"
              onChange={(e) => {
                setRoleFilter(e.target.value);
                setPage(0);
              }}
            >
              <MenuItem value="">All Roles</MenuItem>
              <MenuItem value="CUSTOMER">Customer</MenuItem>
              <MenuItem value="MERCHANT">Merchant</MenuItem>
              <MenuItem value="COURIER">Courier</MenuItem>
              <MenuItem value="ADMIN">Admin</MenuItem>
            </Select>
          </FormControl>

          <FormControl size="small">
            <InputLabel id="status-filter-label">Status</InputLabel>
            <Select
              labelId="status-filter-label"
              value={statusFilter}
              label="Status"
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
            >
              <MenuItem value="">All Statuses</MenuItem>
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="DEACTIVATED">Deactivated</MenuItem>
            </Select>
          </FormControl>
        </Box>
      </Card>

      {/* Users Table */}
      <Card sx={{ borderRadius: tokens.radius.card, border: `1px solid ${tokens.palette.border}`, overflow: 'hidden' }}>
        <TableContainer>
          <Table>
            <TableHead sx={{ backgroundColor: '#F8FAFC' }}>
              <TableRow>
                <TableCell sx={{ fontWeight: 700, color: tokens.palette.navy }}>User</TableCell>
                <TableCell sx={{ fontWeight: 700, color: tokens.palette.navy }}>Role</TableCell>
                <TableCell sx={{ fontWeight: 700, color: tokens.palette.navy }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 700, color: tokens.palette.navy }}>Phone / Vehicle</TableCell>
                <TableCell sx={{ fontWeight: 700, color: tokens.palette.navy }}>Created</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700, color: tokens.palette.navy }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6 }}>
                    <CircularProgress size={32} sx={{ color: tokens.palette.primary }} />
                  </TableCell>
                </TableRow>
              ) : users.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 6, color: tokens.palette.mutedText }}>
                    No users found matching the selected filters.
                  </TableCell>
                </TableRow>
              ) : (
                users.map((u) => {
                  const isActive = u.status === 'ACTIVE';
                  return (
                    <TableRow key={u.id} hover>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text }}>
                          {u.fullName}
                        </Typography>
                        <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                          {u.email}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={u.role} size="small" sx={{ fontWeight: 600, fontSize: '0.75rem' }} />
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={u.status}
                          size="small"
                          sx={{
                            fontWeight: 600,
                            fontSize: '0.75rem',
                            backgroundColor: isActive ? '#DCFCE7' : '#F1F5F9',
                            color: isActive ? '#15803D' : '#64748B',
                          }}
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{u.phone || '—'}</Typography>
                        {u.courierProfile && (
                          <Typography variant="caption" sx={{ color: tokens.palette.mutedText, display: 'block' }}>
                            {u.courierProfile.vehicleType} • {u.courierProfile.vehicleNumber}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                          {u.createdAt ? formatDateTime(u.createdAt) : '—'}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 0.5 }}>
                          <Tooltip title="Edit Details">
                            <IconButton size="small" onClick={() => handleOpenEdit(u)}>
                              <EditOutlinedIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          {isActive ? (
                            <Tooltip title="Deactivate User">
                              <IconButton size="small" color="error" onClick={() => handleDeactivate(u)}>
                                <BlockOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          ) : (
                            <Tooltip title="Activate User">
                              <IconButton size="small" color="success" onClick={() => handleActivate(u)}>
                                <CheckCircleOutlineIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}

                          {u.authProvider !== 'GOOGLE' && (
                            <Tooltip title="Reset Password">
                              <IconButton size="small" onClick={() => handleResetPassword(u)}>
                                <LockResetOutlinedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                        </Box>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>

        <TablePagination
          rowsPerPageOptions={[10, 20, 50]}
          component="div"
          count={totalElements}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, newPage) => setPage(newPage)}
          onRowsPerPageChange={(e) => {
            setRowsPerPage(parseInt(e.target.value, 10));
            setPage(0);
          }}
        />
      </Card>

      {/* Create User Dialog */}
      <Dialog open={createDialogOpen} onClose={() => setCreateDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>
          Create New {createRole === 'COURIER' ? 'Courier' : 'Administrator'}
        </DialogTitle>
        <Box component="form" onSubmit={handleCreateSubmit} noValidate>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <TextField
              label="Full Name"
              required
              fullWidth
              value={createForm.fullName}
              onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })}
              error={Boolean(createErrors.fullName)}
              helperText={createErrors.fullName}
            />

            <TextField
              label="Email Address"
              type="email"
              required
              fullWidth
              value={createForm.email}
              onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
              error={Boolean(createErrors.email)}
              helperText={createErrors.email}
            />

            <TextField
              label="Phone Number"
              fullWidth
              placeholder="e.g. 0771234567"
              value={createForm.phone}
              onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
            />

            {createRole === 'COURIER' && (
              <>
                <FormControl fullWidth>
                  <InputLabel id="vehicle-type-label">Vehicle Type</InputLabel>
                  <Select
                    labelId="vehicle-type-label"
                    value={createForm.vehicleType}
                    label="Vehicle Type"
                    onChange={(e) => setCreateForm({ ...createForm, vehicleType: e.target.value })}
                  >
                    {VEHICLE_TYPES.map((vt) => (
                      <MenuItem key={vt} value={vt}>
                        {vt.replace('_', ' ')}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label="Vehicle Number"
                  required
                  fullWidth
                  placeholder="e.g. WP-BBI-1234"
                  value={createForm.vehicleNumber}
                  onChange={(e) => setCreateForm({ ...createForm, vehicleNumber: e.target.value })}
                  error={Boolean(createErrors.vehicleNumber)}
                  helperText={createErrors.vehicleNumber}
                />
              </>
            )}

            <Alert severity="info" sx={{ mt: 1, borderRadius: tokens.radius.control }}>
              A 16-character secure temporary password will be generated automatically and shown on the next screen.
            </Alert>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5 }}>
            <Button onClick={() => setCreateDialogOpen(false)} disabled={createSubmitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={createSubmitting}
              sx={{ backgroundColor: tokens.palette.primary, fontWeight: 600 }}
            >
              {createSubmitting ? 'Creating...' : 'Create User'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      {/* Edit User Dialog */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 700 }}>
          Edit User ({selectedUser?.email})
        </DialogTitle>
        <Box component="form" onSubmit={handleEditSubmit} noValidate>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <TextField
              label="Full Name"
              required
              fullWidth
              value={editForm.fullName}
              onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })}
            />

            <TextField
              label="Phone Number"
              fullWidth
              value={editForm.phone}
              onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
            />

            {selectedUser?.role === 'COURIER' && (
              <>
                <FormControl fullWidth>
                  <InputLabel id="edit-vehicle-type-label">Vehicle Type</InputLabel>
                  <Select
                    labelId="edit-vehicle-type-label"
                    value={editForm.vehicleType}
                    label="Vehicle Type"
                    onChange={(e) => setEditForm({ ...editForm, vehicleType: e.target.value })}
                  >
                    {VEHICLE_TYPES.map((vt) => (
                      <MenuItem key={vt} value={vt}>
                        {vt.replace('_', ' ')}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label="Vehicle Number"
                  fullWidth
                  value={editForm.vehicleNumber}
                  onChange={(e) => setEditForm({ ...editForm, vehicleNumber: e.target.value })}
                />
              </>
            )}
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5 }}>
            <Button onClick={() => setEditDialogOpen(false)} disabled={editSubmitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="contained"
              disabled={editSubmitting}
              sx={{ backgroundColor: tokens.palette.primary, fontWeight: 600 }}
            >
              {editSubmitting ? 'Saving...' : 'Save Changes'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      {/* One-time Temporary Password Dialog */}
      <Dialog open={Boolean(tempPassword)} onClose={() => setTempPassword(null)} maxWidth="xs" fullWidth>
        <DialogTitle sx={{ fontWeight: 700, color: tokens.palette.text }}>
          Temporary Password Generated
        </DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Alert severity="warning" sx={{ borderRadius: tokens.radius.control }}>
            Copy this temporary password now. For security, it will <strong>not be displayed again</strong>.
          </Alert>

          <Box
            sx={{
              p: 2,
              borderRadius: tokens.radius.control,
              backgroundColor: '#F1F5F9',
              border: `1px solid ${tokens.palette.border}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <Typography variant="body1" sx={{ fontFamily: 'monospace', fontWeight: 700, fontSize: '1.1rem', letterSpacing: '0.05em' }}>
              {tempPassword}
            </Typography>
            <Tooltip title={tempPasswordCopied ? 'Copied!' : 'Copy to clipboard'}>
              <IconButton onClick={handleCopyPassword} color={tempPasswordCopied ? 'success' : 'primary'}>
                {tempPasswordCopied ? <DoneIcon fontSize="small" /> : <ContentCopyOutlinedIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button
            variant="contained"
            fullWidth
            onClick={() => setTempPassword(null)}
            sx={{ backgroundColor: tokens.palette.primary, fontWeight: 600 }}
          >
            I Have Saved This Password
          </Button>
        </DialogActions>
      </Dialog>

      {/* Confirmation Dialog for Deactivate / Activate / Reset Password */}
      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmDialog.title}
        message={confirmDialog.message}
        confirmText={confirmDialog.confirmText}
        isDestructive={confirmDialog.isDestructive}
        loading={confirmDialog.loading}
        onConfirm={confirmDialog.onConfirm}
        onCancel={() => setConfirmDialog((prev) => ({ ...prev, open: false }))}
      />
    </Box>
  );
}

