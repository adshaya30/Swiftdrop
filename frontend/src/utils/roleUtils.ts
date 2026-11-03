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
  { label: 'Customer Portal', path: '/customer', roles: ['CUSTOMER'], icon: 'package' },
  { label: 'My Parcels', path: '/customer/parcels', roles: ['CUSTOMER'], icon: 'package' },
  { label: 'Merchant Hub', path: '/merchant', roles: ['MERCHANT'], icon: 'store' },
  { label: 'Merchant Orders', path: '/merchant/orders', roles: ['MERCHANT'], icon: 'package' },
  { label: 'Courier Runs', path: '/courier', roles: ['COURIER'], icon: 'truck' },
  { label: 'Active Route', path: '/courier/route', roles: ['COURIER'], icon: 'route' },
  { label: 'Admin Console', path: '/admin', roles: ['ADMIN'], icon: 'settings' },
  { label: 'User Directory', path: '/admin/users', roles: ['ADMIN'], icon: 'users' },
];

export function getRoleHomePath(role?: string | null): string {
  if (role && role in ROLE_HOME_PATHS) {
    return ROLE_HOME_PATHS[role as UserRole];
  }
  return '/';
}
