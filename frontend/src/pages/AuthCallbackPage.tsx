import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { getRoleHomePath } from '../utils/roleUtils';
import LoadingSpinner from '../components/LoadingSpinner';
import './AuthCallbackPage.css';

/**
 * Google OAuth Callback Page — /auth/callback
 * SRS v2.1 – Flow 4.5, FR-AUTH-009, FR-AUTH-010
 *
 * Invoked after the backend completes the Google OAuth code exchange,
 * sets the HttpOnly 'sd_refresh' cookie, and redirects the browser here.
 * This page triggers silent session recovery to load the user profile
 * and access token into memory, then navigates to the role-appropriate dashboard.
 */
export default function AuthCallbackPage() {
  const navigate = useNavigate();
  const { refreshSession } = useAuth();

  useEffect(() => {
    let isSubscribed = true;

    async function handleAuth() {
      try {
        const data = await refreshSession();
        if (isSubscribed) {
          const targetPath = getRoleHomePath(data.user.role);
          navigate(targetPath, { replace: true });
        }
      } catch {
        if (isSubscribed) {
          navigate('/login?error=google_failed', { replace: true });
        }
      }
    }

    handleAuth();

    return () => {
      isSubscribed = false;
    };
  }, [navigate, refreshSession]);

  return (
    <div className="auth-callback-container">
      <div className="auth-callback-card">
        <LoadingSpinner label="Completing Google sign-in…" />
        <p className="auth-callback-hint">Securing your session and redirecting you to SwiftDrop…</p>
      </div>
    </div>
  );
}

