import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import CircularProgress from '@mui/material/CircularProgress';
import { useAuth } from '../../../context/AuthContext';
import { ROLE_HOMES } from '../../../routes/routeTable';
import { tokens } from '../../../theme/theme';

/**
 * OAuth Callback page for Member #2 (Flow 4.5, FR-AUTH-009).
 * Restores session via POST /api/v1/auth/refresh and navigates to the role home.
 */
export default function AuthCallbackPage() {
  const { refresh } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function handleOAuthSession() {
      try {
        const result = await refresh();
        if (!isMounted) return;

        const user = result?.user;
        if (user) {
          if (user.mustChangePassword) {
            navigate('/change-password', { replace: true });
          } else if (user.role && ROLE_HOMES[user.role]) {
            navigate(ROLE_HOMES[user.role], { replace: true });
          } else {
            navigate('/customer/dashboard', { replace: true });
          }
        } else {
          navigate('/login?error=google_failed', { replace: true });
        }
      } catch (err) {
        if (!isMounted) return;
        setError(true);
        navigate('/login?error=google_failed', { replace: true });
      }
    }

    handleOAuthSession();

    return () => {
      isMounted = false;
    };
  }, [refresh, navigate]);

  return (
    <Box
      sx={{
        minHeight: '60vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 2,
        p: 3,
      }}
    >
      <CircularProgress size={44} sx={{ color: tokens.palette.primary }} />
      <Typography variant="h6" sx={{ fontWeight: 600, color: tokens.palette.text }}>
        Completing sign in...
      </Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
        Please wait while we set up your session.
      </Typography>
    </Box>
  );
}
