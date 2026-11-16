import React, { useState } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import List from '@mui/material/List';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Avatar from '@mui/material/Avatar';
import MenuIcon from '@mui/icons-material/Menu';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';
import PeopleOutlinedIcon from '@mui/icons-material/PeopleOutlined';
import StoreOutlinedIcon from '@mui/icons-material/StoreOutlined';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LogoutOutlinedIcon from '@mui/icons-material/LogoutOutlined';
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined';
import WebhookOutlinedIcon from '@mui/icons-material/WebhookOutlined';
import { tokens } from '../theme/theme';
import { useAuth } from '../context/AuthContext';
import logoImg from '../assets/logo-white.png';

const SIDEBAR_WIDTH = 250;

export function DashboardLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  const handleMenuOpen = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleLogout = async () => {
    handleMenuClose();
    await logout();
    navigate('/login');
  };

  // Derive nav items by role
  const getNavItems = () => {
    const role = user?.role;
    if (role === 'ADMIN') {
      return [
        { label: 'Dashboard', path: '/admin/dashboard', icon: DashboardOutlinedIcon },
        { label: 'Users', path: '/admin/users', icon: PeopleOutlinedIcon },
        { label: 'Merchants', path: '/admin/merchants', icon: StoreOutlinedIcon },
        { label: 'Parcels', path: '/admin/parcels', icon: LocalShippingOutlinedIcon },
      ];
    } else if (role === 'MERCHANT') {
      return [
        { label: 'Dashboard', path: '/merchant/dashboard', icon: DashboardOutlinedIcon },
        { label: 'Orders', path: '/merchant/orders', icon: LocalShippingOutlinedIcon },
        { label: 'Credentials', path: '/merchant/credentials', icon: LockResetOutlinedIcon },
        { label: 'Webhooks', path: '/merchant/webhook', icon: WebhookOutlinedIcon },
      ];
    } else if (role === 'COURIER') {
      return [
        { label: 'Deliveries', path: '/courier/deliveries', icon: LocalShippingOutlinedIcon },
      ];
    }
    // CUSTOMER default
    return [
      { label: 'Dashboard', path: '/customer/dashboard', icon: DashboardOutlinedIcon },
      { label: 'New Delivery', path: '/deliveries/new', icon: LocalShippingOutlinedIcon },
      { label: 'My Parcels', path: '/deliveries', icon: LocalShippingOutlinedIcon },
    ];
  };

  const navItems = getNavItems();

  const drawerContent = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column', backgroundColor: tokens.palette.navy, color: '#FFFFFF' }}>
      {/* Brand logo header */}
      <Box sx={{ height: 64, px: 3, display: 'flex', alignItems: 'center', borderBottom: '1px solid rgba(255, 255, 255, 0.1)' }}>
        <Box component="img" src={logoImg} alt="SwiftDrop" sx={{ height: 28, width: 'auto' }} />
      </Box>

      {/* Navigation list */}
      <List sx={{ px: 2, py: 3, flexGrow: 1 }}>
        {navItems.map((item) => {
          const isActive = location.pathname === item.path || location.pathname.startsWith(item.path + '/');
          const Icon = item.icon;
          return (
            <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={Link}
                to={item.path}
                onClick={() => setMobileOpen(false)}
                sx={{
                  height: tokens.controlHeight.standard,
                  borderRadius: tokens.radius.control,
                  backgroundColor: isActive ? tokens.palette.primary : 'transparent',
                  color: isActive ? '#FFFFFF' : '#94A3B8',
                  '&:hover': {
                    backgroundColor: isActive ? tokens.palette.primaryHover : 'rgba(255, 255, 255, 0.06)',
                    color: '#FFFFFF',
                  },
                }}
              >
                <ListItemIcon sx={{ color: 'inherit', minWidth: 38 }}>
                  <Icon fontSize="small" />
                </ListItemIcon>
                <ListItemText
                  primary={item.label}
                  primaryTypographyProps={{ fontSize: '0.875rem', fontWeight: isActive ? 600 : 500 }}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>

      {/* User tag footer in drawer */}
      <Box sx={{ p: 2, borderTop: '1px solid rgba(255, 255, 255, 0.1)' }}>
        <Typography variant="caption" sx={{ color: '#94A3B8', display: 'block' }}>
          Logged in as
        </Typography>
        <Typography variant="body2" sx={{ fontWeight: 600, color: '#FFFFFF', textTransform: 'capitalize' }}>
          {user?.fullName || 'User'} ({user?.role?.toLowerCase()})
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', backgroundColor: tokens.palette.pageBg }}>
      {/* Top Bar */}
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${SIDEBAR_WIDTH}px)` },
          ml: { md: `${SIDEBAR_WIDTH}px` },
          backgroundColor: tokens.palette.surface,
          borderBottom: `1px solid ${tokens.palette.border}`,
          color: tokens.palette.text,
        }}
      >
        <Toolbar sx={{ justifyContent: 'space-between', height: 64, px: 3 }}>
          <IconButton
            color="inherit"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ display: { md: 'none' }, mr: 2 }}
          >
            <MenuIcon />
          </IconButton>

          <Box sx={{ flexGrow: 1 }} />

          {/* Account profile button & menu (no global search, no notification bell per Master Section 10) */}
          <IconButton onClick={handleMenuOpen} size="small" sx={{ ml: 2 }}>
            <Avatar
              sx={{
                width: 38,
                height: 38,
                backgroundColor: tokens.palette.softOrange,
                color: tokens.palette.primary,
                fontWeight: 700,
                fontSize: '0.9375rem',
              }}
            >
              {user?.fullName ? user.fullName.charAt(0).toUpperCase() : 'U'}
            </Avatar>
          </IconButton>
          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
            PaperProps={{ sx: { width: 200, borderRadius: tokens.radius.control, mt: 1 } }}
          >
            <Box sx={{ px: 2, py: 1.5, borderBottom: `1px solid ${tokens.palette.border}` }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                {user?.fullName}
              </Typography>
              <Typography variant="caption" sx={{ color: tokens.palette.mutedText }}>
                {user?.role}
              </Typography>
            </Box>
            <MenuItem component={Link} to="/profile" onClick={handleMenuClose}>
              <ListItemIcon>
                <PersonOutlineIcon fontSize="small" />
              </ListItemIcon>
              Profile
            </MenuItem>
            <MenuItem component={Link} to="/change-password" onClick={handleMenuClose}>
              <ListItemIcon>
                <LockResetOutlinedIcon fontSize="small" />
              </ListItemIcon>
              Change Password
            </MenuItem>
            <MenuItem onClick={handleLogout} sx={{ color: tokens.palette.destructive }}>
              <ListItemIcon sx={{ color: tokens.palette.destructive }}>
                <LogoutOutlinedIcon fontSize="small" />
              </ListItemIcon>
              Logout
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      {/* Sidebar - Desktop */}
      <Box
        component="nav"
        sx={{ width: { md: SIDEBAR_WIDTH }, flexShrink: { md: 0 } }}
      >
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: SIDEBAR_WIDTH },
          }}
        >
          {drawerContent}
        </Drawer>

        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: SIDEBAR_WIDTH, borderRight: 'none' },
          }}
          open
        >
          {drawerContent}
        </Drawer>
      </Box>

      {/* Main Content Area */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2.5, md: 4 },
          width: { md: `calc(100% - ${SIDEBAR_WIDTH}px)` },
          mt: '64px',
          minHeight: 'calc(100vh - 64px)',
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}

export default DashboardLayout;
