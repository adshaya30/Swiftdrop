import { type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getRoleHomePath } from '../utils/roleUtils';
import LoadingSpinner from './LoadingSpinner';

interface PublicOnlyRouteProps {
  children: ReactNode;
}

/**
 * Route wrapper for pages that should only be viewed by unauthenticated users (e.g. /login, /register).
 * Authenticated users are redirected to their designated role dashboard or previous location.
 */
export default function PublicOnlyRoute({ children }: PublicOnlyRouteProps) {
  const { user, isAuthenticated, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return <LoadingSpinner fullScreen label="Verifying session…" />;
  }

  if (isAuthenticated && user) {
    // If the user arrived from a previous redirect, return them there; otherwise to their dashboard
    const destination = (location.state as { from?: { pathname: string } })?.from?.pathname || getRoleHomePath(user.role);
    return <Navigate to={destination} replace />;
  }

  return <>{children}</>;
}

