export type UserRole = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'ADMIN';

export interface NavigationItem {
  label: string;
  path: string;
  roles: UserRole[];
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
  { label: 'Customer Portal', path: '/customer', roles: ['CUSTOMER'] },
  { label: 'My Parcels', path: '/customer/parcels', roles: ['CUSTOMER'] },
  { label: 'Merchant Hub', path: '/merchant', roles: ['MERCHANT'] },
  { label: 'Merchant Orders', path: '/merchant/orders', roles: ['MERCHANT'] },
  { label: 'Courier Runs', path: '/courier', roles: ['COURIER'] },
  { label: 'Active Route', path: '/courier/route', roles: ['COURIER'] },
  { label: 'Admin Console', path: '/admin', roles: ['ADMIN'] },
  { label: 'User Directory', path: '/admin/users', roles: ['ADMIN'] },
];

export function getRoleHomePath(role?: string | null): string {
  if (role && role in ROLE_HOME_PATHS) {
    return ROLE_HOME_PATHS[role as UserRole];
  }
  return '/';
}
