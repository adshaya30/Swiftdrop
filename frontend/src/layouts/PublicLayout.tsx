import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getRoleHomePath } from '../utils/roleUtils';
import SwiftDropLogo from '../components/SwiftDropLogo';
import './PublicLayout.css';

interface Props {
  children: ReactNode;
}

/**
 * Public layout shell matching the SwiftDrop Brand Design System.
 * Clean, modern navigation bar with authoritative transparent SwiftDrop logo.
 */
export default function PublicLayout({ children }: Props) {
  const { user, isAuthenticated } = useAuth();
  const roleHome = getRoleHomePath(user?.role);

  return (
    <div className="pub-layout">
      {/* Accessibility Skip Link */}
      <a href="#main-content" className="pub-skip-link">
        Skip to content
      </a>

      {/* Top Navigation Bar */}
      <header className="pub-navbar" role="banner">
        <div className="pub-navbar-inner">
          <div className="pub-brand">
            <SwiftDropLogo variant="dark" size="md" asLink to="/" />
          </div>

          <div className="pub-auth-actions">
            {isAuthenticated ? (
              <div className="pub-user-badge">
                <span className="pub-user-name">Hello, {user?.fullName?.split(' ')[0]}</span>
                <Link to={roleHome} className="sd-btn sd-btn-primary pub-portal-btn">
                  Dashboard &rarr;
                </Link>
              </div>
            ) : (
              <>
                <Link to="/login" className="pub-login-btn">
                  Login
                </Link>
                <Link to="/register" className="pub-signup-btn">
                  Sign Up
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="pub-main" id="main-content">
        {children}
      </main>

      {/* Clean Footer */}
      <footer className="pub-footer" role="contentinfo">
        <div className="pub-footer-inner">
          <div className="pub-footer-brand">
            <SwiftDropLogo variant="dark" size="sm" asLink to="/" />
            <p className="pub-footer-tagline">
              Fast Delivery. Everywhere. Island-wide parcel logistics and courier platform.
            </p>
          </div>
          <div className="pub-footer-meta">
            <div className="pub-security-badges">
              <span className="pub-security-badge">RS256 JWT Encrypted</span>
              <span className="pub-security-badge">HttpOnly Session</span>
              <span className="pub-security-badge">OAuth 2.0 PKCE</span>
            </div>
            <p className="pub-footer-copy">
              &copy; {new Date().getFullYear()} SwiftDrop Logistics System. All rights reserved.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
