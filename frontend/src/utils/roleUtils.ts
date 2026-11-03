export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'ADMIN';

export interface NavigationItem {
  label: string;
  path: string;
  roles: UserRole[];
  icon?: string;
}

export const ROLE_HOME_PATHS: Record<UserRole, string> = {
  CUSTOMER: '/customer',
  MERCHANT: '/merchant',
  COURIER: '/courier',
  ADMIN: '/admin',
};

export const ROLE_LABELS: Record<UserRole, string> = {
  CUSTOMER: 'Customer',
  MERCHANT: 'Merchant',
  COURIER: 'Courier',
  ADMIN: 'System Admin',
};

export const NAVIGATION_ITEMS: NavigationItem[] = [
  // Customer links
  { label: 'Customer Portal', path: '/customer', roles: ['CUSTOMER'], icon: 'dashboard' },
  { label: 'My Parcels', path: '/customer/parcels', roles: ['CUSTOMER'], icon: 'package' },

  // Merchant links (Matches Merchant Dashboard sidebar in reference image)
  { label: 'Dashboard', path: '/merchant', roles: ['MERCHANT'], icon: 'dashboard' },
  { label: 'Create Order', path: '/merchant/create', roles: ['MERCHANT'], icon: 'package-plus' },
  { label: 'Orders', path: '/merchant/orders', roles: ['MERCHANT'], icon: 'package' },
  { label: 'Webhooks', path: '/merchant/webhooks', roles: ['MERCHANT'], icon: 'webhook' },
  { label: 'Settings', path: '/merchant/settings', roles: ['MERCHANT'], icon: 'settings' },

  // Courier links (Matches Courier mobile in reference image)
  { label: 'Courier Runs', path: '/courier', roles: ['COURIER'], icon: 'truck' },
  { label: 'Active Route', path: '/courier/route', roles: ['COURIER'], icon: 'route' },
  { label: 'Delivery History', path: '/courier/history', roles: ['COURIER'], icon: 'history' },

  // Admin links (Matches Operations Dashboard sidebar in reference image)
  { label: 'Dashboard', path: '/admin', roles: ['ADMIN'], icon: 'dashboard' },
  { label: 'Parcels', path: '/admin/parcels', roles: ['ADMIN'], icon: 'package' },
  { label: 'Couriers', path: '/admin/couriers', roles: ['ADMIN'], icon: 'truck' },
  { label: 'Users', path: '/admin/users', roles: ['ADMIN'], icon: 'users' },
  { label: 'Zones & Pricing', path: '/admin/zones', roles: ['ADMIN'], icon: 'zones' },
  { label: 'Reports', path: '/admin/reports', roles: ['ADMIN'], icon: 'reports' },
  { label: 'Settings', path: '/admin/settings', roles: ['ADMIN'], icon: 'settings' },
];

export function getRoleHomePath(role?: string | null): string {
  if (role && role in ROLE_HOME_PATHS) {
    return ROLE_HOME_PATHS[role as UserRole];
  }
  return '/';
}
