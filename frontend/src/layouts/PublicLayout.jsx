import React, { useState } from 'react';
import { Outlet, Link, useNavigate, useLocation } from 'react-router-dom';
import Box from '@mui/material/Box';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import MenuIcon from '@mui/icons-material/Menu';
import CloseIcon from '@mui/icons-material/Close';
import { tokens } from '../theme/theme';
import logoImg from '../assets/logo.png';
import { useAuth } from '../context/AuthContext';

export function PublicLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const { user, status, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const navLinks = [
    { label: 'Track Parcel', path: '/track' },
  ];

  const handleDashboardRedirect = () => {
    if (!user) return;
    if (user.role === 'ADMIN') navigate('/admin/dashboard');
    else if (user.role === 'MERCHANT') navigate('/merchant/dashboard');
    else if (user.role === 'COURIER') navigate('/courier/deliveries');
    else navigate('/customer/dashboard');
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', backgroundColor: tokens.palette.pageBg }}>
      <AppBar
        position="sticky"
        elevation={0}
        sx={{
          backgroundColor: tokens.palette.surface,
          borderBottom: `1px solid ${tokens.palette.border}`,
          color: tokens.palette.text,
        }}
      >
        <Toolbar sx={{ justifyContent: 'space-between', height: 64, px: { xs: 2, md: 4 } }}>
          <Box
            component={Link}
            to="/"
            sx={{ display: 'flex', alignItems: 'center', textDecoration: 'none', gap: 1 }}
          >
            <Box
              component="img"
              src={logoImg}
              alt="SwiftDrop Logo"
              sx={{ height: 32, width: 'auto' }}
            />
          </Box>

          {/* Desktop Navigation */}
          <Box sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center', gap: 2 }}>
            {navLinks.map((link) => (
              <Button
                key={link.path}
                component={Link}
                to={link.path}
                sx={{
                  color: location.pathname === link.path ? tokens.palette.primary : tokens.palette.navy,
                  fontWeight: 600,
                  height: tokens.controlHeight.standard,
                }}
              >
                {link.label}
              </Button>
            ))}

            {status === 'authenticated' && user ? (
              <>
                <Button
                  variant="outlined"
                  onClick={handleDashboardRedirect}
                  sx={{
                    color: tokens.palette.navy,
                    borderColor: tokens.palette.border,
                    fontWeight: 600,
                    height: tokens.controlHeight.standard,
                  }}
                >
                  Dashboard
                </Button>
                <Button
                  variant="text"
                  onClick={logout}
                  sx={{
                    color: tokens.palette.mutedText,
                    fontWeight: 600,
                    height: tokens.controlHeight.standard,
                  }}
                >
                  Logout
                </Button>
              </>
            ) : (
              <>
                <Button
                  component={Link}
                  to="/login"
                  sx={{
                    color: tokens.palette.navy,
                    fontWeight: 600,
                    height: tokens.controlHeight.standard,
                  }}
                >
                  Login
                </Button>
                <Button
                  component={Link}
                  to="/register"
                  variant="contained"
                  sx={{
                    backgroundColor: tokens.palette.primary,
                    height: tokens.controlHeight.standard,
                    px: 3,
                    fontWeight: 600,
                  }}
                >
                  Sign Up
                </Button>
              </>
            )}
          </Box>

          {/* Mobile hamburger */}
          <IconButton
            color="inherit"
            edge="end"
            onClick={handleDrawerToggle}
            sx={{ display: { md: 'none' } }}
            aria-label="open drawer"
          >
            <MenuIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      {/* Mobile Drawer */}
      <Drawer
        anchor="right"
        open={mobileOpen}
        onClose={handleDrawerToggle}
        ModalProps={{ keepMounted: true }}
        PaperProps={{ sx: { width: 280, p: 2 } }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Box component="img" src={logoImg} alt="SwiftDrop" sx={{ height: 28 }} />
          <IconButton onClick={handleDrawerToggle}>
            <CloseIcon />
          </IconButton>
        </Box>
        <List>
          {navLinks.map((link) => (
            <ListItem key={link.path} disablePadding>
              <ListItemButton
                component={Link}
                to={link.path}
                onClick={handleDrawerToggle}
                sx={{ height: 48, borderRadius: tokens.radius.control }}
              >
                <ListItemText primary={link.label} primaryTypographyProps={{ fontWeight: 600 }} />
              </ListItemButton>
            </ListItem>
          ))}
          {status === 'authenticated' && user ? (
            <>
              <ListItem disablePadding sx={{ mt: 2 }}>
                <ListItemButton
                  onClick={() => {
                    handleDrawerToggle();
                    handleDashboardRedirect();
                  }}
                  sx={{ height: 48, borderRadius: tokens.radius.control }}
                >
                  <ListItemText primary="Dashboard" primaryTypographyProps={{ fontWeight: 600 }} />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton
                  onClick={() => {
                    handleDrawerToggle();
                    logout();
                  }}
                  sx={{ height: 48, borderRadius: tokens.radius.control, color: tokens.palette.destructive }}
                >
                  <ListItemText primary="Logout" primaryTypographyProps={{ fontWeight: 600 }} />
                </ListItemButton>
              </ListItem>
            </>
          ) : (
            <>
              <ListItem disablePadding sx={{ mt: 2 }}>
                <ListItemButton
                  component={Link}
                  to="/login"
                  onClick={handleDrawerToggle}
                  sx={{ height: 48, borderRadius: tokens.radius.control }}
                >
                  <ListItemText primary="Login" primaryTypographyProps={{ fontWeight: 600 }} />
                </ListItemButton>
              </ListItem>
              <ListItem disablePadding>
                <ListItemButton
                  component={Link}
                  to="/register"
                  onClick={handleDrawerToggle}
                  sx={{
                    height: 48,
                    borderRadius: tokens.radius.control,
                    backgroundColor: tokens.palette.primary,
                    color: '#fff',
                    '&:hover': { backgroundColor: tokens.palette.primaryHover },
                  }}
                >
                  <ListItemText primary="Sign Up" primaryTypographyProps={{ fontWeight: 600 }} />
                </ListItemButton>
              </ListItem>
            </>
          )}
        </List>
      </Drawer>

      {/* Main Page Content */}
      <Box component="main" sx={{ flexGrow: 1 }}>
        <Outlet />
      </Box>

      {/* Footer */}
      <Box
        component="footer"
        sx={{
          py: 3,
          px: 4,
          backgroundColor: tokens.palette.surface,
          borderTop: `1px solid ${tokens.palette.border}`,
          textAlign: 'center',
        }}
      >
        <Typography variant="body2" sx={{ color: tokens.palette.mutedText }}>
          © {new Date().getFullYear()} SwiftDrop Logistics (Pvt) Ltd. All rights reserved.
        </Typography>
      </Box>
    </Box>
  );
}

export default PublicLayout;
