import { useState, useEffect, useCallback, type FormEvent } from 'react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import {
  adminService,
  type AdminUserSummary,
  type CreateUserPayload,
  type EditUserPayload,
} from '../services/adminService';
import { extractApiError } from '../utils/errorUtils';
import LoadingSpinner from '../components/LoadingSpinner';
import EmptyState from '../components/EmptyState';
import './AdminUsersPage.css';

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();

  // Data states
  const [users, setUsers] = useState<AdminUserSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Pagination states
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [totalElements, setTotalElements] = useState<number>(0);
  const pageSize = 10;

  // Filter states
  const [roleFilter, setRoleFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Modal states
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [showEditModal, setShowEditModal] = useState<boolean>(false);
  const [showDeactivateModal, setShowDeactivateModal] = useState<boolean>(false);
  const [showResetModal, setShowResetModal] = useState<boolean>(false);
  const [tempPasswordModalData, setTempPasswordModalData] = useState<{
    email: string;
    fullName: string;
    temporaryPassword: string;
    title: string;
  } | null>(null);

  // Active user target for edit / deactivate / reset
  const [selectedUser, setSelectedUser] = useState<AdminUserSummary | null>(null);

  // Form states for creation
  const [createEmail, setCreateEmail] = useState('');
  const [createFullName, setCreateFullName] = useState('');
  const [createPhone, setCreatePhone] = useState('');
  const [createRole, setCreateRole] = useState<'ADMIN' | 'COURIER'>('COURIER');
  const [createVehicleType, setCreateVehicleType] = useState('MOTORCYCLE');
  const [createVehicleNumber, setCreateVehicleNumber] = useState('');
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Form states for edit
  const [editFullName, setEditFullName] = useState('');
  const [editPhone, setEditPhone] = useState('');
  const [editVehicleType, setEditVehicleType] = useState('MOTORCYCLE');
  const [editVehicleNumber, setEditVehicleNumber] = useState('');
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // Deactivate action states
  const [deactivateSubmitting, setDeactivateSubmitting] = useState(false);
  const [deactivateError, setDeactivateError] = useState<string | null>(null);

  // Reset password action states
  const [resetSubmitting, setResetSubmitting] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);

  // Copy temporary password indicator
  const [copied, setCopied] = useState(false);

  // Fetch users function
  const fetchUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await adminService.listUsers({
        page,
        size: pageSize,
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        search: searchQuery || undefined,
      });
      setUsers(response.content);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
    } catch (err: unknown) {
      const apiErr = extractApiError(err, 'Failed to load user directory');
      setError(apiErr.detail);
    } finally {
      setLoading(false);
    }
  }, [page, roleFilter, statusFilter, searchQuery]);

  useEffect(() => {
    let ignore = false;
    adminService
      .listUsers({
        page,
        size: pageSize,
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        search: searchQuery || undefined,
      })
      .then((response) => {
        if (!ignore) {
          setUsers(response.content);
          setTotalPages(response.totalPages);
          setTotalElements(response.totalElements);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          const apiErr = extractApiError(err, 'Failed to load user directory');
          setError(apiErr.detail);
          setLoading(false);
        }
      });
    return () => {
      ignore = true;
    };
  }, [page, roleFilter, statusFilter, searchQuery]);

  // Handle open create modal
  const handleOpenCreateModal = () => {
    setCreateEmail('');
    setCreateFullName('');
    setCreatePhone('');
    setCreateRole('COURIER');
    setCreateVehicleType('MOTORCYCLE');
    setCreateVehicleNumber('');
    setCreateError(null);
    setShowCreateModal(true);
  };

  // Submit create user
  const handleCreateSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setCreateError(null);

    if (!createFullName.trim() || createFullName.trim().length < 2) {
      setCreateError('Full name must be at least 2 characters.');
      return;
    }
    if (!createEmail.trim()) {
      setCreateError('Email is required.');
      return;
    }
    if (createRole === 'COURIER' && !createVehicleNumber.trim()) {
      setCreateError('Vehicle number is required for couriers.');
      return;
    }

    setCreateSubmitting(true);
    try {
      const payload: CreateUserPayload = {
        email: createEmail.trim(),
        fullName: createFullName.trim(),
        phone: createPhone.trim() || undefined,
        role: createRole,
        vehicleType: createRole === 'COURIER' ? createVehicleType : undefined,
        vehicleNumber: createRole === 'COURIER' ? createVehicleNumber.trim() : undefined,
      };

      const res = await adminService.createUser(payload);
      setShowCreateModal(false);
      setTempPasswordModalData({
        email: res.user.email,
        fullName: res.user.fullName,
        temporaryPassword: res.temporaryPassword,
        title: `Account Created for ${res.user.fullName}`,
      });
      fetchUsers();
    } catch (err: unknown) {
      const apiErr = extractApiError(err, 'Failed to create user.');
      setCreateError(apiErr.detail);
    } finally {
      setCreateSubmitting(false);
    }
  };

  // Handle open edit modal
  const handleOpenEditModal = (target: AdminUserSummary) => {
    setSelectedUser(target);
    setEditFullName(target.fullName);
    setEditPhone(target.phone || '');
    if (target.courierProfile) {
      setEditVehicleType(target.courierProfile.vehicleType || 'MOTORCYCLE');
      setEditVehicleNumber(target.courierProfile.vehicleNumber || '');
    } else {
      setEditVehicleType('MOTORCYCLE');
      setEditVehicleNumber('');
    }
    setEditError(null);
    setShowEditModal(true);
  };

  // Submit edit user
  const handleEditSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!selectedUser) return;
    setEditError(null);

    if (!editFullName.trim() || editFullName.trim().length < 2) {
      setEditError('Full name must be at least 2 characters.');
      return;
    }

    setEditSubmitting(true);
    try {
      const payload: EditUserPayload = {
        fullName: editFullName.trim(),
        phone: editPhone.trim() || undefined,
        vehicleType: selectedUser.role === 'COURIER' ? editVehicleType : undefined,
        vehicleNumber: selectedUser.role === 'COURIER' ? editVehicleNumber.trim() : undefined,
      };

      await adminService.editUser(selectedUser.id, payload);
      setShowEditModal(false);
      fetchUsers();
    } catch (err: unknown) {
      const apiErr = extractApiError(err, 'Failed to update user.');
      setEditError(apiErr.detail);
    } finally {
      setEditSubmitting(false);
    }
  };

  // Handle activate user
  const handleActivateUser = async (target: AdminUserSummary) => {
    try {
      await adminService.activateUser(target.id);
      fetchUsers();
    } catch (err: unknown) {
      const apiErr = extractApiError(err, 'Failed to activate user.');
      alert(apiErr.detail);
    }
  };

  // Handle open deactivate modal
  const handleOpenDeactivateModal = (target: AdminUserSummary) => {
    setSelectedUser(target);
    setDeactivateError(null);
    setShowDeactivateModal(true);
  };

  // Confirm deactivate user
  const handleConfirmDeactivate = async () => {
    if (!selectedUser) return;
    setDeactivateSubmitting(true);
    setDeactivateError(null);

    try {
      await adminService.deactivateUser(selectedUser.id);
      setShowDeactivateModal(false);
      fetchUsers();
    } catch (err: unknown) {
      let errCode: string | undefined;
      if (axios.isAxiosError(err)) {
        errCode = err.response?.data?.code;
      }
      if (errCode === 'COURIER_HAS_ACTIVE_DELIVERIES') {
        setDeactivateError(
          'Cannot deactivate courier: The courier currently has deliveries in progress. All active deliveries must be completed or reassigned first.'
        );
      } else if (errCode === 'SERVICE_UNAVAILABLE') {
        setDeactivateError(
          'Delivery service is currently unreachable. Deactivation verification cannot proceed. Please try again shortly.'
        );
      } else if (errCode === 'SELF_DEACTIVATION_NOT_ALLOWED') {
        setDeactivateError('You cannot deactivate your own administrator account.');
      } else {
        const apiErr = extractApiError(err, 'Failed to deactivate user.');
        setDeactivateError(apiErr.detail);
      }
    } finally {
      setDeactivateSubmitting(false);
    }
  };

  // Handle open reset password modal
  const handleOpenResetModal = (target: AdminUserSummary) => {
    setSelectedUser(target);
    setResetError(null);
    setShowResetModal(true);
  };

  // Confirm reset password
  const handleConfirmReset = async () => {
    if (!selectedUser) return;
    setResetSubmitting(true);
    setResetError(null);

    try {
      const res = await adminService.resetPassword(selectedUser.id);
      setShowResetModal(false);
      setTempPasswordModalData({
        email: selectedUser.email,
        fullName: selectedUser.fullName,
        temporaryPassword: res.temporaryPassword,
        title: `Password Reset for ${selectedUser.fullName}`,
      });
      fetchUsers();
    } catch (err: unknown) {
      const apiErr = extractApiError(err, 'Failed to reset user password.');
      setResetError(apiErr.detail);
    } finally {
      setResetSubmitting(false);
    }
  };

  // Copy temporary password to clipboard
  const handleCopyPassword = () => {
    if (!tempPasswordModalData) return;
    navigator.clipboard.writeText(tempPasswordModalData.temporaryPassword);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="sd-admin-users-container">
      {/* Header */}
      <div className="sd-admin-header">
        <div>
          <h1 className="sd-admin-title">User Directory &amp; Access Management</h1>
          <p className="sd-admin-subtitle">
            Manage system administrators, courier staff, customer accounts, and security statuses.
          </p>
        </div>
        <div className="sd-admin-header-actions">
          <button
            type="button"
            className="sd-btn-secondary"
            onClick={() => fetchUsers()}
            disabled={loading}
          >
            ↻ Refresh
          </button>
          <button
            type="button"
            className="sd-btn-primary"
            onClick={handleOpenCreateModal}
          >
            + Create User
          </button>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="sd-admin-filters-card">
        <div className="sd-filter-group" style={{ flex: '1 1 240px' }}>
          <label className="sd-filter-label" htmlFor="user-search">
            Search
          </label>
          <input
            id="user-search"
            type="text"
            className="sd-filter-input"
            placeholder="Search by name or email…"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setPage(0);
            }}
          />
        </div>

        <div className="sd-filter-group">
          <label className="sd-filter-label" htmlFor="role-filter">
            Role
          </label>
          <select
            id="role-filter"
            className="sd-filter-select"
            value={roleFilter}
            onChange={(e) => {
              setRoleFilter(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All Roles</option>
            <option value="ADMIN">Admin</option>
            <option value="COURIER">Courier</option>
            <option value="CUSTOMER">Customer</option>
            <option value="MERCHANT">Merchant</option>
          </select>
        </div>

        <div className="sd-filter-group">
          <label className="sd-filter-label" htmlFor="status-filter">
            Status
          </label>
          <select
            id="status-filter"
            className="sd-filter-select"
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="DEACTIVATED">Deactivated</option>
            <option value="SUSPENDED">Suspended</option>
            <option value="PENDING_VERIFICATION">Pending Verification</option>
          </select>
        </div>
      </div>

      {/* Error state */}
      {error && (
        <div className="sd-alert-banner sd-alert-error" role="alert">
          <strong>Error:</strong> {error}
        </div>
      )}

      {/* Table Card */}
      <div className="sd-admin-table-card">
        {loading ? (
          <div style={{ padding: '3rem' }}>
            <LoadingSpinner label="Loading users directory…" />
          </div>
        ) : users.length === 0 ? (
          <div style={{ padding: '3rem' }}>
            <EmptyState
              title="No Users Found"
              description="No user accounts matched your search and filter criteria."
              icon="👥"
            />
          </div>
        ) : (
          <div className="sd-table-wrapper">
            <table className="sd-users-table">
              <thead>
                <tr>
                  <th>User</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Phone</th>
                  <th>Auth Provider</th>
                  <th>Courier Info</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => {
                  const isSelf = currentUser?.id === u.id;
                  return (
                    <tr key={u.id}>
                      <td>
                        <div className="sd-user-name-cell">
                          <span className="sd-user-fullname">
                            {u.fullName} {isSelf && <small style={{ color: '#38bdf8' }}>(You)</small>}
                          </span>
                          <span className="sd-user-email">{u.email}</span>
                        </div>
                      </td>
                      <td>
                        <span className={`sd-role-tag sd-role-${u.role.toLowerCase()}`}>
                          {u.role}
                        </span>
                      </td>
                      <td>
                        <span className={`sd-status-tag sd-status-${u.status.toLowerCase()}`}>
                          {u.status}
                        </span>
                      </td>
                      <td>{u.phone || '—'}</td>
                      <td>
                        <span style={{ fontSize: '0.8125rem' }}>{u.authProvider}</span>
                      </td>
                      <td>
                        {u.courierProfile ? (
                          <div style={{ fontSize: '0.8125rem' }}>
                            <strong>{u.courierProfile.vehicleType}</strong>
                            <div>{u.courierProfile.vehicleNumber}</div>
                          </div>
                        ) : (
                          '—'
                        )}
                      </td>
                      <td>
                        <div className="sd-table-actions">
                          <button
                            type="button"
                            className="sd-action-btn-sm"
                            title="Edit User Details"
                            onClick={() => handleOpenEditModal(u)}
                          >
                            Edit
                          </button>

                          <button
                            type="button"
                            className="sd-action-btn-sm"
                            title="Reset Temporary Password"
                            onClick={() => handleOpenResetModal(u)}
                          >
                            Reset Pwd
                          </button>

                          {u.status === 'ACTIVE' ? (
                            <button
                              type="button"
                              className="sd-action-btn-sm sd-action-btn-danger-sm"
                              title={isSelf ? 'Cannot deactivate your own account' : 'Deactivate Account'}
                              disabled={isSelf}
                              onClick={() => handleOpenDeactivateModal(u)}
                            >
                              Deactivate
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="sd-action-btn-sm sd-action-btn-success-sm"
                              title="Activate Account"
                              onClick={() => handleActivateUser(u)}
                            >
                              Activate
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        <div className="sd-pagination-bar">
          <span>
            Showing <strong>{users.length}</strong> of <strong>{totalElements}</strong> users
          </span>
          <div className="sd-pagination-buttons">
            <button
              type="button"
              className="sd-btn-secondary"
              style={{ padding: '0.35rem 0.75rem', fontSize: '0.8125rem' }}
              disabled={page === 0 || loading}
              onClick={() => setPage((prev) => Math.max(0, prev - 1))}
            >
              &larr; Previous
            </button>
            <span style={{ alignSelf: 'center', margin: '0 0.5rem' }}>
              Page {page + 1} of {Math.max(1, totalPages)}
            </span>
            <button
              type="button"
              className="sd-btn-secondary"
              style={{ padding: '0.35rem 0.75rem', fontSize: '0.8125rem' }}
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((prev) => prev + 1)}
            >
              Next &rarr;
            </button>
          </div>
        </div>
      </div>

      {/* CREATE USER MODAL */}
      {showCreateModal && (
        <div className="sd-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="create-modal-title">
          <div className="sd-modal-content">
            <div className="sd-modal-header">
              <h2 id="create-modal-title" className="sd-modal-title">Create New Account</h2>
              <button
                type="button"
                className="sd-modal-close-btn"
                onClick={() => setShowCreateModal(false)}
                disabled={createSubmitting}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateSubmit}>
              <div className="sd-modal-body">
                {createError && (
                  <div className="sd-alert-banner sd-alert-error" role="alert">
                    {createError}
                  </div>
                )}

                <div className="sd-form-group">
                  <label className="sd-form-label" htmlFor="create-role">
                    Account Role *
                  </label>
                  <select
                    id="create-role"
                    className="sd-form-select"
                    value={createRole}
                    onChange={(e) => setCreateRole(e.target.value as 'ADMIN' | 'COURIER')}
                  >
                    <option value="COURIER">Courier (Delivery Staff)</option>
                    <option value="ADMIN">System Administrator</option>
                  </select>
                </div>

                <div className="sd-form-group">
                  <label className="sd-form-label" htmlFor="create-name">
                    Full Name *
                  </label>
                  <input
                    id="create-name"
                    type="text"
                    className="sd-form-input"
                    placeholder="e.g. Priyantha Silva"
                    value={createFullName}
                    onChange={(e) => setCreateFullName(e.target.value)}
                    required
                  />
                </div>

                <div className="sd-form-group">
                  <label className="sd-form-label" htmlFor="create-email">
                    Email Address *
                  </label>
                  <input
                    id="create-email"
                    type="email"
                    className="sd-form-input"
                    placeholder="e.g. priyantha@swiftdrop.lk"
                    value={createEmail}
                    onChange={(e) => setCreateEmail(e.target.value)}
                    required
                  />
                </div>

                <div className="sd-form-group">
                  <label className="sd-form-label" htmlFor="create-phone">
                    Phone Number (Optional)
                  </label>
                  <input
                    id="create-phone"
                    type="tel"
                    className="sd-form-input"
                    placeholder="e.g. +94771234567"
                    value={createPhone}
                    onChange={(e) => setCreatePhone(e.target.value)}
                  />
                </div>

                {createRole === 'COURIER' && (
                  <>
                    <div className="sd-form-group">
                      <label className="sd-form-label" htmlFor="create-vehicle-type">
                        Vehicle Type *
                      </label>
                      <select
                        id="create-vehicle-type"
                        className="sd-form-select"
                        value={createVehicleType}
                        onChange={(e) => setCreateVehicleType(e.target.value)}
                      >
                        <option value="MOTORCYCLE">Motorcycle</option>
                        <option value="THREE_WHEELER">Three-Wheeler</option>
                        <option value="CAR">Car</option>
                        <option value="VAN">Van</option>
                        <option value="TRUCK">Truck</option>
                        <option value="BICYCLE">Bicycle</option>
                      </select>
                    </div>

                    <div className="sd-form-group">
                      <label className="sd-form-label" htmlFor="create-vehicle-number">
                        Vehicle Plate Number *
                      </label>
                      <input
                        id="create-vehicle-number"
                        type="text"
                        className="sd-form-input"
                        placeholder="e.g. WP-BC-1234"
                        value={createVehicleNumber}
                        onChange={(e) => setCreateVehicleNumber(e.target.value)}
                        required
                      />
                    </div>
                  </>
                )}
              </div>
              <div className="sd-modal-footer">
                <button
                  type="button"
                  className="sd-btn-secondary"
                  onClick={() => setShowCreateModal(false)}
                  disabled={createSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="sd-btn-primary"
                  disabled={createSubmitting}
                >
                  {createSubmitting ? 'Creating…' : 'Create & Generate Password'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* EDIT USER MODAL */}
      {showEditModal && selectedUser && (
        <div className="sd-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="edit-modal-title">
          <div className="sd-modal-content">
            <div className="sd-modal-header">
              <h2 id="edit-modal-title" className="sd-modal-title">Edit User Details</h2>
              <button
                type="button"
                className="sd-modal-close-btn"
                onClick={() => setShowEditModal(false)}
                disabled={editSubmitting}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleEditSubmit}>
              <div className="sd-modal-body">
                {editError && (
                  <div className="sd-alert-banner sd-alert-error" role="alert">
                    {editError}
                  </div>
                )}

                <div className="sd-form-group">
                  <label className="sd-form-label">Email (Immutable)</label>
                  <input
                    type="text"
                    className="sd-form-input"
                    value={selectedUser.email}
                    disabled
                    style={{ opacity: 0.6 }}
                  />
                </div>

                <div className="sd-form-group">
                  <label className="sd-form-label" htmlFor="edit-name">
                    Full Name *
                  </label>
                  <input
                    id="edit-name"
                    type="text"
                    className="sd-form-input"
                    value={editFullName}
                    onChange={(e) => setEditFullName(e.target.value)}
                    required
                  />
                </div>

                <div className="sd-form-group">
                  <label className="sd-form-label" htmlFor="edit-phone">
                    Phone Number
                  </label>
                  <input
                    id="edit-phone"
                    type="tel"
                    className="sd-form-input"
                    value={editPhone}
                    onChange={(e) => setEditPhone(e.target.value)}
                  />
                </div>

                {selectedUser.role === 'COURIER' && (
                  <>
                    <div className="sd-form-group">
                      <label className="sd-form-label" htmlFor="edit-vehicle-type">
                        Vehicle Type
                      </label>
                      <select
                        id="edit-vehicle-type"
                        className="sd-form-select"
                        value={editVehicleType}
                        onChange={(e) => setEditVehicleType(e.target.value)}
                      >
                        <option value="MOTORCYCLE">Motorcycle</option>
                        <option value="THREE_WHEELER">Three-Wheeler</option>
                        <option value="CAR">Car</option>
                        <option value="VAN">Van</option>
                        <option value="TRUCK">Truck</option>
                        <option value="BICYCLE">Bicycle</option>
                      </select>
                    </div>

                    <div className="sd-form-group">
                      <label className="sd-form-label" htmlFor="edit-vehicle-number">
                        Vehicle Plate Number
                      </label>
                      <input
                        id="edit-vehicle-number"
                        type="text"
                        className="sd-form-input"
                        value={editVehicleNumber}
                        onChange={(e) => setEditVehicleNumber(e.target.value)}
                      />
                    </div>
                  </>
                )}
              </div>
              <div className="sd-modal-footer">
                <button
                  type="button"
                  className="sd-btn-secondary"
                  onClick={() => setShowEditModal(false)}
                  disabled={editSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="sd-btn-primary"
                  disabled={editSubmitting}
                >
                  {editSubmitting ? 'Saving…' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DEACTIVATE CONFIRMATION MODAL */}
      {showDeactivateModal && selectedUser && (
        <div className="sd-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="deactivate-modal-title">
          <div className="sd-modal-content">
            <div className="sd-modal-header">
              <h2 id="deactivate-modal-title" className="sd-modal-title">Confirm Deactivation</h2>
              <button
                type="button"
                className="sd-modal-close-btn"
                onClick={() => setShowDeactivateModal(false)}
                disabled={deactivateSubmitting}
              >
                &times;
              </button>
            </div>
            <div className="sd-modal-body">
              {deactivateError ? (
                <div className="sd-alert-banner sd-alert-error" role="alert">
                  {deactivateError}
                </div>
              ) : (
                <div className="sd-alert-banner sd-alert-warning">
                  Are you sure you want to deactivate <strong>{selectedUser.fullName}</strong> ({selectedUser.email})?
                  <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.8125rem' }}>
                    Deactivating this account will immediately revoke all active refresh tokens and block future sign-ins.
                    {selectedUser.role === 'COURIER' &&
                      ' Note: Couriers with active deliveries in progress cannot be deactivated.'}
                  </p>
                </div>
              )}
            </div>
            <div className="sd-modal-footer">
              <button
                type="button"
                className="sd-btn-secondary"
                onClick={() => setShowDeactivateModal(false)}
                disabled={deactivateSubmitting}
              >
                Cancel
              </button>
              <button
                type="button"
                className="sd-btn-danger"
                onClick={handleConfirmDeactivate}
                disabled={deactivateSubmitting}
              >
                {deactivateSubmitting ? 'Deactivating…' : 'Deactivate Account'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* RESET PASSWORD CONFIRMATION MODAL */}
      {showResetModal && selectedUser && (
        <div className="sd-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="reset-modal-title">
          <div className="sd-modal-content">
            <div className="sd-modal-header">
              <h2 id="reset-modal-title" className="sd-modal-title">Reset User Password</h2>
              <button
                type="button"
                className="sd-modal-close-btn"
                onClick={() => setShowResetModal(false)}
                disabled={resetSubmitting}
              >
                &times;
              </button>
            </div>
            <div className="sd-modal-body">
              {resetError ? (
                <div className="sd-alert-banner sd-alert-error" role="alert">
                  {resetError}
                </div>
              ) : (
                <div className="sd-alert-banner sd-alert-warning">
                  Generate a new temporary password for <strong>{selectedUser.fullName}</strong> ({selectedUser.email})?
                  <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.8125rem' }}>
                    All current active sessions will be terminated. The user will be required to change this temporary
                    password upon next sign-in.
                  </p>
                </div>
              )}
            </div>
            <div className="sd-modal-footer">
              <button
                type="button"
                className="sd-btn-secondary"
                onClick={() => setShowResetModal(false)}
                disabled={resetSubmitting}
              >
                Cancel
              </button>
              <button
                type="button"
                className="sd-btn-primary"
                onClick={handleConfirmReset}
                disabled={resetSubmitting}
              >
                {resetSubmitting ? 'Generating…' : 'Generate New Password'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* TEMPORARY PASSWORD SINGLE-VIEW MODAL */}
      {tempPasswordModalData && (
        <div className="sd-modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="temp-modal-title">
          <div className="sd-modal-content">
            <div className="sd-modal-header">
              <h2 id="temp-modal-title" className="sd-modal-title">{tempPasswordModalData.title}</h2>
              <button
                type="button"
                className="sd-modal-close-btn"
                onClick={() => setTempPasswordModalData(null)}
              >
                &times;
              </button>
            </div>
            <div className="sd-modal-body">
              <div className="sd-alert-banner sd-alert-warning">
                <strong>Security Notice:</strong> This temporary password is shown <strong>only once</strong> and cannot be retrieved later. Copy it now and deliver it securely to the user.
              </div>

              <div className="sd-temp-password-box">
                <div className="sd-temp-password-value">
                  {tempPasswordModalData.temporaryPassword}
                </div>
                <button
                  type="button"
                  className="sd-copy-btn"
                  onClick={handleCopyPassword}
                >
                  {copied ? '✓ Copied to clipboard!' : '📋 Copy Password'}
                </button>
              </div>

              <p style={{ fontSize: '0.8125rem', color: '#94a3b8', margin: 0, textAlign: 'center' }}>
                Account: <strong>{tempPasswordModalData.email}</strong>
              </p>
            </div>
            <div className="sd-modal-footer">
              <button
                type="button"
                className="sd-btn-primary"
                onClick={() => setTempPasswordModalData(null)}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

