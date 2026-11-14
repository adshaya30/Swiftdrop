import React, { lazy, Suspense } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { routeTable, ROLE_HOMES } from './routeTable';
import { useAuth } from '../context/AuthContext';
import PublicLayout from '../layouts/PublicLayout';
import DashboardLayout from '../layouts/DashboardLayout';
import CourierLayout from '../layouts/CourierLayout';
import LoadingState from '../components/LoadingState';
import NotBuiltYet from '../components/NotBuiltYet';

/**
 * Dynamic page resolution via Vite's import.meta.glob per Master Prompt Section 7.4.
 * Other members will add /src/pages/<folder>/index.jsx without needing to edit this route table.
 */
const pageModules = import.meta.glob('../pages/**/index.jsx');

/**
 * Resolves a page component dynamically, falling back to NotBuiltYet.
 */
export function resolvePageComponent(pageFolder, routeTitle) {
  const globKey = `../pages/${pageFolder}/index.jsx`;
  if (pageModules[globKey]) {
    const LazyComponent = lazy(pageModules[globKey]);
    return function DynamicPage(props) {
      return (
        <Suspense fallback={<LoadingState message="Loading page..." />}>
          <LazyComponent {...props} />
        </Suspense>
      );
    };
  }

  return function FallbackPage() {
    return <NotBuiltYet routeTitle={routeTitle} />;
  };
}

/**
 * Route guard enforcing SRS Section 7.1 authentication, role authorization,
 * and FR-AUTH-014 mustChangePassword redirect.
 */
function RouteGuard({ route, children }) {
  const { user, status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <LoadingState message="Checking session..." minHeight="80vh" />;
  }

  // 1. If publicOnly (e.g. /login, /register) and user is already authenticated -> redirect to role home
  if (route.publicOnly && status === 'authenticated' && user) {
    const home = ROLE_HOMES[user.role] || '/customer/dashboard';
    return <Navigate to={home} replace />;
  }

  // 2. If route requires specific roles
  if (route.roles && route.roles.length > 0) {
    if (status !== 'authenticated' || !user) {
      return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />;
    }

    // Forced password change gate (FR-AUTH-014)
    if (user.mustChangePassword && !route.allowMustChangePassword) {
      return <Navigate to="/change-password" replace />;
    }

    if (!route.roles.includes(user.role)) {
      const home = ROLE_HOMES[user.role] || '/customer/dashboard';
      return <Navigate to={home} replace />;
    }
  }

  // 3. For authenticated users on non-whitelisted routes while mustChangePassword is active
  if (status === 'authenticated' && user?.mustChangePassword && !route.allowMustChangePassword && !route.publicOnly && location.pathname !== '/track') {
    return <Navigate to="/change-password" replace />;
  }

  return children;
}

export function AppRouter() {
  // Group routes by layout
  const publicRoutes = routeTable.filter((r) => r.layout === 'public');
  const dashboardRoutes = routeTable.filter((r) => r.layout === 'dashboard');
  const courierRoutes = routeTable.filter((r) => r.layout === 'courier');

  return (
    <Routes>
      {/* Public Layout */}
      <Route element={<PublicLayout />}>
        {publicRoutes.map((route) => {
          const PageComponent = resolvePageComponent(route.pageFolder, route.title);
          return (
            <Route
              key={route.path}
              path={route.path}
              element={
                <RouteGuard route={route}>
                  <PageComponent />
                </RouteGuard>
              }
            />
          );
        })}
      </Route>

      {/* Dashboard Layout */}
      <Route element={<DashboardLayout />}>
        {dashboardRoutes.map((route) => {
          const PageComponent = resolvePageComponent(route.pageFolder, route.title);
          return (
            <Route
              key={route.path}
              path={route.path}
              element={
                <RouteGuard route={route}>
                  <PageComponent />
                </RouteGuard>
              }
            />
          );
        })}
      </Route>

      {/* Courier Layout */}
      <Route element={<CourierLayout />}>
        {courierRoutes.map((route) => {
          const PageComponent = resolvePageComponent(route.pageFolder, route.title);
          return (
            <Route
              key={route.path}
              path={route.path}
              element={
                <RouteGuard route={route}>
                  <PageComponent />
                </RouteGuard>
              }
            />
          );
        })}
      </Route>

      {/* Catch-all 404 redirect to / */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default AppRouter;
