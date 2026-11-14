import React from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import { tokens } from '../../../theme/theme';

/**
 * Short explanation of the API-only order flow with the token request example (SRS 3.6, FR-AUTH-019, UC-M04).
 * Orders are created through the API only; this dashboard has no create-order form (master prompt section 10).
 * The example uses placeholders, never a real secret.
 */
export default function ApiUsageNote() {
  const origin = typeof window !== 'undefined' ? window.location.origin : '';
  const example = [
    `curl -X POST ${origin}/api/v1/oauth/token \\`,
    '  -d grant_type=client_credentials \\',
    '  -d client_id=YOUR_CLIENT_ID \\',
    '  -d client_secret=YOUR_CLIENT_SECRET \\',
    '  -d "scope=orders:read orders:write"',
  ].join('\n');

  return (
    <Card
      component="section"
      aria-labelledby="api-usage-title"
      sx={{ mt: 3, p: { xs: 2, sm: 3 }, borderRadius: tokens.radius.card, border: `1px solid ${tokens.palette.border}` }}
    >
      <Typography id="api-usage-title" variant="h6" component="h2" sx={{ fontWeight: 700, color: tokens.palette.text, mb: 1 }}>
        Using your credentials
      </Typography>
      <Typography variant="body2" sx={{ color: tokens.palette.mutedText, mb: 1.5 }}>
        Orders are created through the API only. Your system exchanges a client ID and secret for an access token with{' '}
        <strong>POST /api/v1/oauth/token</strong>, then sends orders to <strong>/api/v1/merchant/orders</strong> with that
        token. A token is valid for 15 minutes and carries the scopes you ask for (<code>orders:read</code>,{' '}
        <code>orders:write</code>). Credentials can only be created and revoked; a secret is shown once, when it is created.
      </Typography>
      <Box
        component="pre"
        tabIndex={0}
        aria-label="Example token request"
        sx={{
          m: 0,
          p: 2,
          overflowX: 'auto',
          maxWidth: '100%',
          borderRadius: tokens.radius.control,
          backgroundColor: tokens.palette.mutedSurface,
          color: tokens.palette.text,
          fontSize: '0.8125rem',
          lineHeight: 1.6,
        }}
      >
        <code>{example}</code>
      </Box>
    </Card>
  );
}
