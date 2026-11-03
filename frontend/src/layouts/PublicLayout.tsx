import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getRoleHomePath } from '../utils/roleUtils';
import SwiftDropLogo from '../components/SwiftDropLogo';
import './PublicLayout.css';

interface Props {
  children: ReactNode;
}

/**
 * Public layout shell matching the SwiftDrop Brand Design System.
 * Clean, modern navigation bar with authoritative SwiftDrop logo and links.
 */
export default function PublicLayout({ children }: Props) {
  const { user, isAuthenticated } = useAuth();
  const location = useLocation();
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

          <nav className="pub-nav-links" aria-label="Main Navigation">
            <Link
              to="/#track"
              className={`pub-nav-link ${location.hash === '#track' ? 'active' : ''}`}
            >
              Track
            </Link>
            <Link
              to="/#services"
              className={`pub-nav-link ${location.hash === '#services' ? 'active' : ''}`}
            >
              Services
            </Link>
            <Link
              to="/#business"
              className={`pub-nav-link ${location.hash === '#business' ? 'active' : ''}`}
            >
              For Business
            </Link>
            <Link
              to="/#about"
              className={`pub-nav-link ${location.hash === '#about' ? 'active' : ''}`}
            >
              About
            </Link>
          </nav>

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
              Fast Delivery. Everywhere. Island-wide courier and logistics platform.
            </p>
          </div>
          <div className="pub-footer-links">
            <div className="pub-footer-col">
              <h4>Solutions</h4>
              <Link to="/#track">Parcel Tracking</Link>
              <Link to="/#services">Same-Day Courier</Link>
              <Link to="/#business">Merchant API</Link>
            </div>
            <div className="pub-footer-col">
              <h4>Company</h4>
              <Link to="/#about">About Us</Link>
              <Link to="/login">Courier Portal</Link>
              <Link to="/admin">Operations Console</Link>
            </div>
            <div className="pub-footer-col">
              <h4>Security</h4>
              <span>RS256 JWT Encrypted</span>
              <span>HttpOnly Cookies</span>
              <span>OAuth 2.0 PKCE</span>
            </div>
          </div>
        </div>
        <div className="pub-footer-bottom">
          <p>&copy; {new Date().getFullYear()} SwiftDrop Logistics System. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
