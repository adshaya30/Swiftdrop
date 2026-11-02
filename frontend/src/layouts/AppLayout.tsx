import { useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { NAVIGATION_ITEMS, ROLE_LABELS, type UserRole } from '../utils/roleUtils';
import './AppLayout.css';

interface AppLayoutProps {
  children: ReactNode;
}

export default function AppLayout({ children }: AppLayoutProps) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

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

  return (
    <div className="sd-app-layout">
      {/* Accessibility Skip Link */}
      <a href="#main-content" className="sd-skip-link">
        Skip to main content
      </a>

      {/* Header / Navigation Bar */}
      <header className="sd-navbar" role="banner">
        <div className="sd-navbar-inner">
          <div className="sd-brand-group">
            <Link to="/" className="sd-brand-link" aria-label="SwiftDrop Home">
              <span className="sd-brand-badge" aria-hidden="true">SD</span>
              <span className="sd-brand-name">SwiftDrop</span>
            </Link>

            {/* Role Badge */}
            <span className={`sd-role-pill sd-role-${userRole.toLowerCase()}`}>
              {roleLabel}
            </span>
          </div>

          {/* Desktop Navigation */}
          <nav className="sd-nav-links" aria-label="Primary Navigation">
            {visibleNavItems.map(item => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `sd-nav-item ${isActive ? 'sd-nav-item-active' : ''}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          {/* User Profile & Sign Out */}
          <div className="sd-user-controls">
            <div className="sd-user-info" aria-label="Logged in user profile">
              <span className="sd-user-avatar" aria-hidden="true">
                {user?.fullName?.charAt(0).toUpperCase() || 'U'}
              </span>
              <div className="sd-user-meta">
                <span className="sd-user-name">{user?.fullName || 'User'}</span>
                <span className="sd-user-id">ID: {user?.id?.slice(0, 8)}…</span>
              </div>
            </div>

            <button
              type="button"
              className="sd-signout-btn"
              onClick={handleLogout}
              disabled={signingOut}
              aria-label="Sign out of account"
            >
              {signingOut ? 'Signing out…' : 'Sign Out'}
            </button>

            {/* Mobile Menu Toggle */}
            <button
              type="button"
              className="sd-mobile-toggle"
              onClick={() => setMobileMenuOpen(prev => !prev)}
              aria-label="Toggle navigation menu"
              aria-expanded={mobileMenuOpen}
            >
              ☰
            </button>
          </div>
        </div>

        {/* Mobile Dropdown Navigation */}
        {mobileMenuOpen && (
          <nav className="sd-mobile-menu" aria-label="Mobile Navigation">
            {visibleNavItems.map(item => (
              <NavLink
                key={item.path}
                to={item.path}
                className="sd-mobile-item"
                onClick={() => setMobileMenuOpen(false)}
              >
                {item.label}
              </NavLink>
            ))}
            <button
              type="button"
              className="sd-mobile-signout"
              onClick={() => {
                setMobileMenuOpen(false);
                handleLogout();
              }}
              disabled={signingOut}
            >
              Sign Out
            </button>
          </nav>
        )}
      </header>

      {/* Main Content Area */}
      <main id="main-content" className="sd-main-content" tabIndex={-1}>
        {children}
      </main>

      {/* Application Footer */}
      <footer className="sd-footer" role="contentinfo">
        <div className="sd-footer-inner">
          <p className="sd-footer-copy">
            &copy; {new Date().getFullYear()} SwiftDrop Parcel &amp; Courier Logistics System.
          </p>
          <div className="sd-footer-status">
            <span className="sd-status-dot" aria-hidden="true" />
            <span>Authenticated Session Active</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
