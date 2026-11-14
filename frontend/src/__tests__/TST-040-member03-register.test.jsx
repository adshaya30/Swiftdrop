import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../api/merchantAccount', () => ({
  registerMerchant: vi.fn(),
  listCredentials: vi.fn(),
  createCredential: vi.fn(),
  revokeCredential: vi.fn(),
}));

import { registerMerchant } from '../api/merchantAccount';
import { ApiError } from '../api/http';
import RegisterMerchantPage from '../pages/merchant/register/index.jsx';
import { passwordRules, registerMerchantSchema, toRequestBody } from '../pages/merchant/register/schema';

const VALID = {
  businessName: 'Lanka Crafts',
  businessRegistrationNo: '',
  contactName: 'Nimal Perera',
  phone: '077 123 4567',
  email: 'shop@example.com',
  password: 'Str0ng!Pass#1',
  addressLine1: '12 Galle Road',
  city: 'Colombo',
};

function renderPage() {
  return render(
    <MemoryRouter>
      <RegisterMerchantPage />
    </MemoryRouter>
  );
}

const field = (label) => screen.getByLabelText(label, { exact: false });

async function fillForm(user, overrides = {}) {
  const values = { ...VALID, ...overrides };
  await user.type(field('Business name'), values.businessName);
  if (values.businessRegistrationNo) await user.type(field('Business registration number'), values.businessRegistrationNo);
  await user.type(field('Contact person'), values.contactName);
  await user.type(field('Phone number'), values.phone);
  await user.type(field('Email address'), values.email);
  await user.type(screen.getByLabelText('Password'), values.password);
  await user.type(field('Business address'), values.addressLine1);
  await user.type(field('City'), values.city);
}

const submit = () => screen.getByRole('button', { name: /register business/i });

describe('TST-040 Member 3: merchant registration page (FR-AUTH-016, UC-M01)', { timeout: 20000 }, () => {
  beforeEach(() => {
    registerMerchant.mockReset();
  });

  it('shows every field with a visible label and marks the registration number as optional', () => {
    renderPage();
    for (const label of ['Business name', 'Business registration number', 'Contact person', 'Phone number',
      'Email address', 'Business address', 'City']) {
      expect(field(label)).toBeInTheDocument();
    }
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByText('(optional)')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: /register your business/i })).toBeInTheDocument();
  });

  it('shows required-field errors and does not call the API when the form is empty', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(submit());

    expect(await screen.findByText('Business name is required')).toBeInTheDocument();
    expect(screen.getByText('Contact name is required')).toBeInTheDocument();
    expect(screen.getByText('Phone number is required')).toBeInTheDocument();
    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(screen.getByText('Address is required')).toBeInTheDocument();
    expect(screen.getByText('City is required')).toBeInTheDocument();
    expect(registerMerchant).not.toHaveBeenCalled();
  });

  it('enforces the server limits: business name 2-120, contact 2-100, address 150, city 80, reg no 40, email 254', async () => {
    const user = userEvent.setup();
    renderPage();
    fireEvent.change(field('Business name'), { target: { value: 'A' } });
    fireEvent.change(field('Contact person'), { target: { value: 'B'.repeat(101) } });
    fireEvent.change(field('Business registration number'), { target: { value: 'R'.repeat(41) } });
    fireEvent.change(field('Business address'), { target: { value: 'x'.repeat(151) } });
    fireEvent.change(field('City'), { target: { value: 'y'.repeat(81) } });
    fireEvent.change(field('Email address'), { target: { value: `${'e'.repeat(250)}@x.lk` } });
    await user.click(submit());

    expect(await screen.findByText('Business name must be at least 2 characters')).toBeInTheDocument();
    expect(screen.getByText('Contact name must not exceed 100 characters')).toBeInTheDocument();
    expect(screen.getByText('Registration number must not exceed 40 characters')).toBeInTheDocument();
    expect(screen.getByText('Address must not exceed 150 characters')).toBeInTheDocument();
    expect(screen.getByText('City must not exceed 80 characters')).toBeInTheDocument();
    expect(screen.getByText('Email must not exceed 254 characters')).toBeInTheDocument();
    expect(registerMerchant).not.toHaveBeenCalled();
  });

  it('accepts the boundary lengths (business name 120, address 150, city 80, registration number 40)', () => {
    const result = registerMerchantSchema.safeParse({
      ...VALID,
      businessName: 'n'.repeat(120),
      businessRegistrationNo: 'r'.repeat(40),
      contactName: 'c'.repeat(100),
      addressLine1: 'a'.repeat(150),
      city: 'c'.repeat(80),
    });
    expect(result.success).toBe(true);
  });

  it.each([
    ['0771234567', true],
    ['+94771234567', true],
    ['94771234567', true],
    ['077 123 4567', true],
    ['(077)-123-4567', true],
    ['0112345678', true],
    ['12345', false],
    ['+15551234567', false],
    ['077123456', false],
  ])('validates the phone number %s -> %s', (phone, ok) => {
    expect(registerMerchantSchema.safeParse({ ...VALID, phone }).success).toBe(ok);
  });

  it('updates the live password hints while typing, with text and icon (never colour alone)', async () => {
    const user = userEvent.setup();
    renderPage();
    const list = screen.getByRole('list', { name: /password requirements/i });
    expect(within(list).getAllByText(/^Not met:/)).toHaveLength(5); // only the email rule is met while empty

    await user.type(screen.getByLabelText('Password'), 'abc');
    expect(within(list).getByText('One lowercase letter').previousSibling).toHaveTextContent('Met:');
    expect(within(list).getByText('One uppercase letter').previousSibling).toHaveTextContent('Not met:');

    await user.clear(screen.getByLabelText('Password'));
    await user.type(screen.getByLabelText('Password'), 'Str0ng!Pass#1');
    expect(within(list).queryAllByText(/^Not met:/)).toHaveLength(0);
    expect(within(list).getAllByText(/^Met:/)).toHaveLength(6);
  });

  it('flags a password that contains the part of the email before the @', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.type(field('Email address'), 'shopowner@example.com');
    await user.type(screen.getByLabelText('Password'), 'Xshopowner9!Zz');
    const list = screen.getByRole('list', { name: /password requirements/i });
    expect(within(list).getByText(/does not contain the part of your email/i).previousSibling).toHaveTextContent('Not met:');
  });

  it('shows what a weak password is missing on submit and blocks the request', async () => {
    const user = userEvent.setup();
    renderPage();
    await fillForm(user, { password: 'alllowercase' });
    await user.click(submit());
    expect(await screen.findByText(/Password needs: one uppercase letter, one digit, one special character/i)).toBeInTheDocument();
    expect(registerMerchant).not.toHaveBeenCalled();
  });

  it('toggles password visibility with an accessible button', async () => {
    const user = userEvent.setup();
    renderPage();
    const input = screen.getByLabelText('Password');
    expect(input).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Show password' }));
    expect(input).toHaveAttribute('type', 'text');
    await user.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(input).toHaveAttribute('type', 'password');
  });

  it('submits trimmed values without the empty optional field, then shows "waiting for admin approval" and no login', async () => {
    const user = userEvent.setup();
    registerMerchant.mockResolvedValue({ message: 'pending' });
    renderPage();
    await fillForm(user, { businessName: '  Lanka Crafts  ' });
    await user.click(submit());

    await waitFor(() => expect(registerMerchant).toHaveBeenCalledTimes(1));
    expect(registerMerchant).toHaveBeenCalledWith({
      businessName: 'Lanka Crafts',
      contactName: 'Nimal Perera',
      phone: '077 123 4567',
      email: 'shop@example.com',
      password: 'Str0ng!Pass#1',
      addressLine1: '12 Galle Road',
      city: 'Colombo',
    });
    expect(await screen.findByText('Your registration is waiting for admin approval')).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument(); // the form (and the password) is gone
    expect(screen.getByRole('link', { name: /go to login/i })).toHaveAttribute('href', '/login');
  });

  it('sends the registration number when it is filled in', async () => {
    const user = userEvent.setup();
    registerMerchant.mockResolvedValue({ message: 'pending' });
    renderPage();
    await fillForm(user, { businessRegistrationNo: 'PV 12345' });
    await user.click(submit());
    await waitFor(() => expect(registerMerchant).toHaveBeenCalled());
    expect(registerMerchant.mock.calls[0][0].businessRegistrationNo).toBe('PV 12345');
  });

  it('disables the button while the request runs so a double click sends one request', async () => {
    const user = userEvent.setup();
    let resolve;
    registerMerchant.mockImplementation(() => new Promise((r) => { resolve = r; }));
    renderPage();
    await fillForm(user);
    await user.click(submit());

    const running = await screen.findByRole('button', { name: /registering/i });
    expect(running).toBeDisabled();
    fireEvent.click(running); // a disabled button ignores clicks
    expect(registerMerchant).toHaveBeenCalledTimes(1);
    resolve({ message: 'pending' });
    expect(await screen.findByText('Your registration is waiting for admin approval')).toBeInTheDocument();
  });

  it('shows server errors[] next to their fields and keeps everything the user typed', async () => {
    const user = userEvent.setup();
    registerMerchant.mockRejectedValue(new ApiError({
      status: 400,
      code: 'VALIDATION_ERROR',
      detail: 'Request validation failed.',
      errors: [{ field: 'phone', message: 'Must be a valid Sri Lankan phone number' }],
    }));
    renderPage();
    await fillForm(user);
    await user.click(submit());

    expect(await screen.findByText('Must be a valid Sri Lankan phone number')).toBeInTheDocument();
    expect(field('Business name')).toHaveValue('Lanka Crafts');
    expect(field('Phone number')).toHaveValue('077 123 4567');
    expect(field('Email address')).toHaveValue('shop@example.com');
    expect(screen.getByLabelText('Password')).toHaveValue('Str0ng!Pass#1');
    expect(screen.queryByText('Your registration is waiting for admin approval')).not.toBeInTheDocument();
  });

  it('shows EMAIL_ALREADY_REGISTERED on the email field and keeps the input', async () => {
    const user = userEvent.setup();
    registerMerchant.mockRejectedValue(new ApiError({
      status: 409,
      code: 'EMAIL_ALREADY_REGISTERED',
      detail: 'An account with this email address already exists.',
    }));
    renderPage();
    await fillForm(user);
    await user.click(submit());

    expect(await screen.findByText(/an account with this email address already exists/i)).toBeInTheDocument();
    expect(field('Email address')).toHaveAttribute('aria-invalid', 'true');
    expect(field('City')).toHaveValue('Colombo');
  });

  it('shows other server errors in an alert (for example rate limiting) and keeps the input', async () => {
    const user = userEvent.setup();
    registerMerchant.mockRejectedValue(new ApiError({ status: 429, code: 'RATE_LIMITED', detail: 'Too many requests, try again later.' }));
    renderPage();
    await fillForm(user);
    await user.click(submit());

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many requests, try again later.');
    expect(field('Business name')).toHaveValue('Lanka Crafts');
  });

  it('shows a clear message when the server cannot be reached', async () => {
    const user = userEvent.setup();
    registerMerchant.mockRejectedValue(new TypeError('Failed to fetch'));
    renderPage();
    await fillForm(user);
    await user.click(submit());
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be reached/i);
  });
});

describe('TST-040 Member 3: registration schema helpers', () => {
  it('passwordRules reports each rule', () => {
    const rules = Object.fromEntries(passwordRules('Str0ng!Pass#1', 'x@y.lk').map((r) => [r.id, r.met]));
    expect(rules).toEqual({ length: true, upper: true, lower: true, digit: true, special: true, email: true });
    expect(passwordRules('short', '').find((r) => r.id === 'length').met).toBe(false);
    expect(passwordRules('a'.repeat(65), '').find((r) => r.id === 'length').met).toBe(false);
  });

  it('toRequestBody drops an empty registration number only', () => {
    expect(toRequestBody({ ...VALID }).businessRegistrationNo).toBeUndefined();
    expect(toRequestBody({ ...VALID, businessRegistrationNo: 'X1' }).businessRegistrationNo).toBe('X1');
  });
});
