import { useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import {
  Package,
  Truck,
  Users,
  Settings,
  MapPin,
  LogOut,
  Bell,
  Menu,
  X,
  User as UserIcon,
  KeyRound,
  Store,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { NAVIGATION_ITEMS, ROLE_LABELS, type UserRole } from '../utils/roleUtils';
import SwiftDropLogo from '../components/SwiftDropLogo';
import './AppLayout.css';

interface AppLayoutProps {
  children: ReactNode;
}

function getNavIcon(iconName?: string) {
  switch (iconName) {
    case 'package':
      return <Package size={18} />;
    case 'store':
      return <Store size={18} />;
    case 'truck':
      return <Truck size={18} />;
    case 'users':
      return <Users size={18} />;
    case 'settings':
      return <Settings size={18} />;
    case 'route':
      return <MapPin size={18} />;
    default:
      return <Package size={18} />;
  }
}

export default function AppLayout({ children }: AppLayoutProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [signingOut, setSigningOut] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);

  const userRole = (user?.role as UserRole) || 'CUSTOMER';
  const roleLabel = ROLE_LABELS[userRole] || userRole;

  // Filter navigation items accessible to this user's role
  const visibleNavItems = NAVIGATION_ITEMS.filter(item =>
    item.roles.includes(userRole)
  );

  const handleLogout = async () => {
    setSigningOut(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } finally {
      setSigningOut(false);
    }
  };

  const getPageHeaderTitle = () => {
    if (location.pathname.startsWith('/admin/users')) return 'User Directory';
    if (location.pathname.startsWith('/admin')) return 'Admin Console';
    if (location.pathname.startsWith('/merchant')) return 'Merchant Hub';
    if (location.pathname.startsWith('/courier')) return 'Courier Portal';
    if (location.pathname.startsWith('/customer')) return 'Customer Portal';
    if (location.pathname === '/profile') return 'My Profile';
    if (location.pathname === '/change-password') return 'Change Password';
    return 'SwiftDrop Portal';
  };

  return (
    <div className="sd-app-shell">
      {/* Accessibility Skip Link */}
      <a href="#main-content" className="sd-skip-link">
        Skip to main content
      </a>

      {/* ------------------------------------------------------------------ */}
      {/* Dark Navy Sidebar                                                  */}
      {/* ------------------------------------------------------------------ */}
      <aside className={`sd-sidebar ${mobileMenuOpen ? 'mobile-open' : ''}`} aria-label="Sidebar Navigation">
        <div className="sd-sidebar-header">
          <SwiftDropLogo variant="light" size="md" asLink to="/" />
          <button
            type="button"
            className="sd-sidebar-close"
            onClick={() => setMobileMenuOpen(false)}
            aria-label="Close navigation menu"
          >
            <X size={20} />
          </button>
        </div>

        {/* Navigation list */}
        <nav className="sd-sidebar-nav" aria-label="Role Navigation">
          <div className="sd-sidebar-group-title">Navigation</div>
          {visibleNavItems.map(item => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/admin' || item.path === '/merchant' || item.path === '/courier' || item.path === '/customer'}
              className={({ isActive }) =>
                `sd-sidebar-link ${isActive ? 'active' : ''}`
              }
              onClick={() => setMobileMenuOpen(false)}
            >
              <span className="sd-sidebar-icon">{getNavIcon(item.icon)}</span>
              <span className="sd-sidebar-text">{item.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Sidebar Footer with Logout */}
        <div className="sd-sidebar-footer">
          <button
            type="button"
            className="sd-sidebar-logout"
            onClick={handleLogout}
            disabled={signingOut}
            aria-label="Sign out of account"
          >
            <LogOut size={18} />
            <span>{signingOut ? 'Signing out…' : 'Logout'}</span>
          </button>
        </div>
      </aside>

      {/* Backdrop for mobile drawer */}
      {mobileMenuOpen && (
        <div
          className="sd-sidebar-backdrop"
          onClick={() => setMobileMenuOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* ------------------------------------------------------------------ */}
      {/* Main Content Area + Top Bar                                        */}
      {/* ------------------------------------------------------------------ */}
      <div className="sd-main-wrapper">
        <header className="sd-topbar">
          <div className="sd-topbar-left">
            <button
              type="button"
              className="sd-topbar-toggle"
              onClick={() => setMobileMenuOpen(true)}
              aria-label="Open navigation menu"
            >
              <Menu size={22} />
            </button>
            <div className="sd-topbar-title-row">
              <h1 className="sd-topbar-title">{getPageHeaderTitle()}</h1>
            </div>
          </div>

          <div className="sd-topbar-right">
            {/* Notification Bell */}
            <button
              type="button"
              className="sd-icon-btn"
              aria-label="View notifications"
              title="Notifications"
            >
              <Bell size={18} />
              <span className="sd-notif-dot" aria-hidden="true" />
            </button>

            {/* User Dropdown */}
            <div className="sd-user-menu-wrapper">
              <button
                type="button"
                className="sd-user-btn"
                onClick={() => setUserDropdownOpen(prev => !prev)}
                aria-expanded={userDropdownOpen}
                aria-haspopup="true"
              >
                <span className="sd-avatar">
                  {user?.fullName?.charAt(0).toUpperCase() || 'U'}
                </span>
                <div className="sd-user-details">
                  <span className="sd-user-display-name">{user?.fullName || 'User'}</span>
                  <span className={`sd-role-pill sd-role-${userRole.toLowerCase()}`}>
                    {roleLabel}
                  </span>
                </div>
                <span className="sd-chevron">▾</span>
              </button>

              {userDropdownOpen && (
                <div className="sd-user-dropdown" role="menu">
                  <div className="sd-dropdown-header">
                    <p className="sd-dropdown-name">{user?.fullName}</p>
                    <p className="sd-dropdown-email">{user?.email}</p>
                  </div>
                  <div className="sd-dropdown-divider" />
                  <Link
                    to="/profile"
                    className="sd-dropdown-item"
                    role="menuitem"
                    onClick={() => setUserDropdownOpen(false)}
                  >
                    <UserIcon size={16} />
                    <span>My Profile</span>
                  </Link>
                  <Link
                    to="/change-password"
                    className="sd-dropdown-item"
                    role="menuitem"
                    onClick={() => setUserDropdownOpen(false)}
                  >
                    <KeyRound size={16} />
                    <span>Change Password</span>
                  </Link>
                  <div className="sd-dropdown-divider" />
                  <button
                    type="button"
                    className="sd-dropdown-item sd-dropdown-logout"
                    role="menuitem"
                    onClick={() => {
                      setUserDropdownOpen(false);
                      handleLogout();
                    }}
                    disabled={signingOut}
                  >
                    <LogOut size={16} />
                    <span>Sign Out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        {/* Scrollable Page Body */}
        <main id="main-content" className="sd-page-body" tabIndex={-1}>
          {children}
        </main>

        <footer className="sd-app-footer">
          <div className="sd-app-footer-inner">
            <span>&copy; {new Date().getFullYear()} SwiftDrop Logistics System. All rights reserved.</span>
            <div className="sd-app-status">
              <span className="sd-status-dot" aria-hidden="true" />
              <span>RS256 Verified Session</span>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
}
