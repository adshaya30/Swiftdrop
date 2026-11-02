import { type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getRoleHomePath, type UserRole } from '../utils/roleUtils';
import LoadingSpinner from './LoadingSpinner';

interface ProtectedRouteProps {
  children: ReactNode;
  allowedRoles?: UserRole[];
}

/**
 * Route guard enforcing:
 * 1. Session initialization wait (no flash of login screen)
 * 2. Authentication check (redirects to /login preserving target location)
 * 3. Forced password change redirect (FR-AUTH-003 / Member 2 contract)
 * 4. Role-based authorization check (FR-UI-002)
 */
export default function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const { user, isAuthenticated, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return <LoadingSpinner fullScreen label="Verifying session…" />;
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  // Contract for Member 2: force password change if flagged
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" state={{ from: location }} replace />;
  }

  // Check role restriction if specified
  if (allowedRoles && allowedRoles.length > 0) {
    const userRole = user.role as UserRole;
    if (!allowedRoles.includes(userRole)) {
      const authorizedHome = getRoleHomePath(user.role);
      return <Navigate to={authorizedHome} replace />;
    }
  }

  return <>{children}</>;
}
