import * as z from 'zod';

/**
 * Client-side validation for POST /auth/register-merchant. Mirrors the server limits of FR-AUTH-016 and the password
 * policy of FR-AUTH-001. The server stays the authority and its errors[] are shown as well.
 */

export const LIMITS = {
  businessName: { min: 2, max: 120 },
  businessRegistrationNo: { max: 40 },
  contactName: { min: 2, max: 100 },
  email: { max: 254 },
  password: { min: 10, max: 64 },
  addressLine1: { min: 1, max: 150 },
  city: { min: 1, max: 80 },
};

/** Sri Lankan numbers: 07XXXXXXXX, +947XXXXXXXX, 947XXXXXXXX and landlines, after removing separators. */
const PHONE_PATTERN = /^(?:\+94|94|0)[1-9][0-9]{8}$/;

export function emailLocalPart(email) {
  if (!email || !email.includes('@')) return '';
  return email.split('@')[0].trim().toLowerCase();
}

/** The live password rules shown while typing (same rules as the server's PasswordPolicy). */
export function passwordRules(password = '', email = '') {
  const local = emailLocalPart(email);
  return [
    {
      id: 'length',
      label: `${LIMITS.password.min} to ${LIMITS.password.max} characters`,
      met: password.length >= LIMITS.password.min && password.length <= LIMITS.password.max,
    },
    { id: 'upper', label: 'One uppercase letter', met: /[A-Z]/.test(password) },
    { id: 'lower', label: 'One lowercase letter', met: /[a-z]/.test(password) },
    { id: 'digit', label: 'One digit', met: /[0-9]/.test(password) },
    { id: 'special', label: 'One special character', met: /[^a-zA-Z0-9]/.test(password) },
    {
      id: 'email',
      label: 'Does not contain the part of your email before the @',
      met: !local || !password.toLowerCase().includes(local),
    },
  ];
}

const CHARACTER_RULES = ['upper', 'lower', 'digit', 'special'];

const requiredText = (label, { min, max }) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required`)
    .min(min, `${label} must be at least ${min} characters`)
    .max(max, `${label} must not exceed ${max} characters`);

export const registerMerchantSchema = z
  .object({
    businessName: requiredText('Business name', LIMITS.businessName),
    businessRegistrationNo: z
      .string()
      .trim()
      .max(LIMITS.businessRegistrationNo.max, `Registration number must not exceed ${LIMITS.businessRegistrationNo.max} characters`),
    contactName: requiredText('Contact name', LIMITS.contactName),
    email: z
      .string()
      .trim()
      .min(1, 'Email is required')
      .max(LIMITS.email.max, `Email must not exceed ${LIMITS.email.max} characters`)
      .email('Please enter a valid email address'),
    phone: z
      .string()
      .trim()
      .min(1, 'Phone number is required')
      .refine(
        (value) => PHONE_PATTERN.test(value.replace(/[\s\-().]/g, '')),
        'Please enter a valid Sri Lankan phone number (for example 0771234567 or +94771234567)'
      ),
    password: z
      .string()
      .min(1, 'Password is required')
      .min(LIMITS.password.min, `Password must be at least ${LIMITS.password.min} characters`)
      .max(LIMITS.password.max, `Password must not exceed ${LIMITS.password.max} characters`)
      .superRefine((password, ctx) => {
        const missing = passwordRules(password).filter((rule) => !rule.met && CHARACTER_RULES.includes(rule.id));
        if (missing.length > 0) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: `Password needs: ${missing.map((rule) => rule.label.toLowerCase()).join(', ')}`,
          });
        }
      }),
    addressLine1: requiredText('Address', LIMITS.addressLine1),
    city: requiredText('City', LIMITS.city),
  })
  .superRefine((data, ctx) => {
    const local = emailLocalPart(data.email);
    if (local && data.password.toLowerCase().includes(local)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['password'],
        message: 'Password must not contain the part of your email before the @',
      });
    }
  });

/** Request body: empty optional fields are left out. */
export function toRequestBody(values) {
  const body = { ...values };
  if (!body.businessRegistrationNo) {
    delete body.businessRegistrationNo;
  }
  return body;
}
