import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ROLE_HOME_PATHS,
  ROLE_LABELS,
  getRoleHomePath,
  NAVIGATION_ITEMS,
} from '../utils/roleUtils.ts';

test('SwiftDrop Brand & Role Navigation System', async (t) => {
  await t.test('ROLE_HOME_PATHS maps all roles to appropriate portals', () => {
    assert.equal(ROLE_HOME_PATHS.CUSTOMER, '/customer');
    assert.equal(ROLE_HOME_PATHS.MERCHANT, '/merchant');
    assert.equal(ROLE_HOME_PATHS.COURIER, '/courier');
    assert.equal(ROLE_HOME_PATHS.ADMIN, '/admin');
  });

  await t.test('ROLE_LABELS provides user-facing display names', () => {
    assert.equal(ROLE_LABELS.CUSTOMER, 'Customer');
    assert.equal(ROLE_LABELS.MERCHANT, 'Merchant');
    assert.equal(ROLE_LABELS.COURIER, 'Courier');
    assert.equal(ROLE_LABELS.ADMIN, 'System Admin');
  });

  await t.test('getRoleHomePath returns root for unknown or null role', () => {
    assert.equal(getRoleHomePath(null), '/');
    assert.equal(getRoleHomePath(undefined), '/');
    assert.equal(getRoleHomePath('UNKNOWN'), '/');
  });

  await t.test('getRoleHomePath returns exact role home path for valid roles', () => {
    assert.equal(getRoleHomePath('CUSTOMER'), '/customer');
    assert.equal(getRoleHomePath('MERCHANT'), '/merchant');
    assert.equal(getRoleHomePath('COURIER'), '/courier');
    assert.equal(getRoleHomePath('ADMIN'), '/admin');
  });

  await t.test('NAVIGATION_ITEMS contains expected routes and icons for each role', () => {
    const adminItems = NAVIGATION_ITEMS.filter(item => item.roles.includes('ADMIN'));
    const merchantItems = NAVIGATION_ITEMS.filter(item => item.roles.includes('MERCHANT'));
    const courierItems = NAVIGATION_ITEMS.filter(item => item.roles.includes('COURIER'));
    const customerItems = NAVIGATION_ITEMS.filter(item => item.roles.includes('CUSTOMER'));

    assert.ok(adminItems.some(i => i.path === '/admin'));
    assert.ok(adminItems.some(i => i.path === '/admin/users'));
    assert.ok(merchantItems.some(i => i.path === '/merchant'));
    assert.ok(courierItems.some(i => i.path === '/courier'));
    assert.ok(customerItems.some(i => i.path === '/customer'));
  });
});
