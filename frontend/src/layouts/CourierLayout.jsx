import React from 'react';
import { Outlet, Link, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import { tokens } from '../theme/theme';
import { useAuth } from '../context/AuthContext';
import logoImg from '../assets/logo.png';

export function CourierLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <Box sx={{ minHeight: '100vh', backgroundColor: tokens.palette.pageBg, display: 'flex', flexDirection: 'column' }}>
      <AppBar
        position="sticky"
        elevation={0}
        sx={{
          backgroundColor: tokens.palette.surface,
          borderBottom: `1px solid ${tokens.palette.border}`,
          color: tokens.palette.text,
        }}
      >
        <Toolbar sx={{ justifyContent: 'space-between', height: 60, px: 2 }}>
          <Box component={Link} to="/courier/deliveries" sx={{ display: 'flex', alignItems: 'center', textDecoration: 'none' }}>
            <Box component="img" src={logoImg} alt="SwiftDrop" sx={{ height: 26 }} />
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="body2" sx={{ fontWeight: 600, color: tokens.palette.text, display: { xs: 'none', sm: 'block' } }}>
              {user?.fullName}
            </Typography>
            <Button
              size="small"
              onClick={handleLogout}
              startIcon={<LogoutOutlinedIcon fontSize="small" />}
              sx={{
                color: tokens.palette.mutedText,
                height: 44,
                borderRadius: tokens.radius.control,
              }}
            >
              Logout
            </Button>
          </Box>
        </Toolbar>
      </AppBar>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: '100%',
          maxWidth: 640,
          mx: 'auto',
          p: { xs: 2, sm: 3 },
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}

export default CourierLayout;
