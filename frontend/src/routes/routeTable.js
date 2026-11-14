/**
 * SRS Section 7.1 route table matrix.
 * Note: Static routes are placed BEFORE parameterized routes to prevent route collisions.
 */
export const routeTable = [
  // ----------------------------------------------------
  // Public Routes (layout: 'public', roles: null)
  // ----------------------------------------------------
  {
    path: '/',
    title: 'Home',
    layout: 'public',
    pageFolder: 'public/home',
    roles: null,
  },
  {
    path: '/track',
    title: 'Track Parcel',
    layout: 'public',
    pageFolder: 'public/track',
    roles: null,
  },
  {
    path: '/track/:trackingNumber',
    title: 'Parcel Tracking',
    layout: 'public',
    pageFolder: 'public/track-detail',
    roles: null,
  },
  {
    path: '/login',
    title: 'Login',
    layout: 'public',
    pageFolder: 'auth/login',
    roles: null,
    publicOnly: true,
  },
  {
    path: '/register',
    title: 'Customer Registration',
    layout: 'public',
    pageFolder: 'auth/register',
    roles: null,
    publicOnly: true,
  },
  {
    path: '/register-merchant',
    title: 'Merchant Registration',
    layout: 'public',
    pageFolder: 'auth/register-merchant',
    roles: null,
    publicOnly: true,
  },
  {
    path: '/auth/callback',
    title: 'OAuth Callback',
    layout: 'public',
    pageFolder: 'auth/callback',
    roles: null,
  },
  {
    path: '/change-password',
    title: 'Change Password',
    layout: 'dashboard',
    pageFolder: 'auth/change-password',
    roles: ['CUSTOMER', 'MERCHANT', 'COURIER', 'ADMIN'],
    allowMustChangePassword: true,
  },
  {
    path: '/profile',
    title: 'User Profile',
    layout: 'dashboard',
    pageFolder: 'auth/profile',
    roles: ['CUSTOMER', 'MERCHANT', 'COURIER', 'ADMIN'],
  },

  // ----------------------------------------------------
  // Customer Routes (layout: 'dashboard', roles: ['CUSTOMER'])
  // ----------------------------------------------------
  {
    path: '/customer/dashboard',
    title: 'Customer Dashboard',
    layout: 'dashboard',
    pageFolder: 'customer/dashboard',
    roles: ['CUSTOMER'],
  },
  {
    path: '/deliveries/new',
    title: 'Create Delivery',
    layout: 'dashboard',
    pageFolder: 'customer/deliveries/new',
    roles: ['CUSTOMER'],
  },
  {
    path: '/deliveries',
    title: 'My Parcels',
    layout: 'dashboard',
    pageFolder: 'customer/deliveries/list',
    roles: ['CUSTOMER'],
  },
  {
    path: '/deliveries/:id',
    title: 'Parcel Details',
    layout: 'dashboard',
    pageFolder: 'customer/deliveries/detail',
    roles: ['CUSTOMER'],
  },

  // ----------------------------------------------------
  // Merchant Routes (layout: 'dashboard', roles: ['MERCHANT'])
  // ----------------------------------------------------
  {
    path: '/merchant/dashboard',
    title: 'Merchant Dashboard',
    layout: 'dashboard',
    pageFolder: 'merchant/dashboard',
    roles: ['MERCHANT'],
  },
  {
    path: '/merchant/orders',
    title: 'Merchant Orders',
    layout: 'dashboard',
    pageFolder: 'merchant/orders',
    roles: ['MERCHANT'],
  },
  {
    path: '/merchant/orders/:id',
    title: 'Merchant Order Details',
    layout: 'dashboard',
    pageFolder: 'merchant/order-detail',
    roles: ['MERCHANT'],
  },
  {
    path: '/merchant/credentials',
    title: 'API Credentials',
    layout: 'dashboard',
    pageFolder: 'merchant/credentials',
    roles: ['MERCHANT'],
  },
  {
    path: '/merchant/webhook',
    title: 'Webhook Settings',
    layout: 'dashboard',
    pageFolder: 'merchant/webhook',
    roles: ['MERCHANT'],
  },

  // ----------------------------------------------------
  // Courier Routes (layout: 'courier', roles: ['COURIER'])
  // ----------------------------------------------------
  {
    path: '/courier/deliveries',
    title: 'My Deliveries',
    layout: 'courier',
    pageFolder: 'courier/deliveries',
    roles: ['COURIER'],
  },
  {
    path: '/courier/deliveries/:id',
    title: 'Delivery Run',
    layout: 'courier',
    pageFolder: 'courier/delivery-detail',
    roles: ['COURIER'],
  },

  // ----------------------------------------------------
  // Admin Routes (layout: 'dashboard', roles: ['ADMIN'])
  // Static routes first before parameterized!
  // ----------------------------------------------------
  {
    path: '/admin/dashboard',
    title: 'Admin Dashboard',
    layout: 'dashboard',
    pageFolder: 'admin/dashboard',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/users',
    title: 'User Management',
    layout: 'dashboard',
    pageFolder: 'admin/users',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/merchants/pending',
    title: 'Pending Merchants',
    layout: 'dashboard',
    pageFolder: 'admin/merchants/pending',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/merchants',
    title: 'Merchant Directory',
    layout: 'dashboard',
    pageFolder: 'admin/merchants',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/merchants/:id',
    title: 'Merchant Review',
    layout: 'dashboard',
    pageFolder: 'admin/merchants/detail',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/parcels/unassigned',
    title: 'Unassigned Parcels',
    layout: 'dashboard',
    pageFolder: 'admin/parcels/unassigned',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/parcels',
    title: 'Parcel Registry',
    layout: 'dashboard',
    pageFolder: 'admin/parcels',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/parcels/:id',
    title: 'Parcel Inspector',
    layout: 'dashboard',
    pageFolder: 'admin/parcels/detail',
    roles: ['ADMIN'],
  },
  {
    path: '/admin/dispatch',
    title: 'Dispatch Center',
    layout: 'dashboard',
    pageFolder: 'admin/dispatch',
    roles: ['ADMIN'],
  },
];

export const ROLE_HOMES = {
  CUSTOMER: '/customer/dashboard',
  MERCHANT: '/merchant/dashboard',
  COURIER: '/courier/deliveries',
  ADMIN: '/admin/dashboard',
};
