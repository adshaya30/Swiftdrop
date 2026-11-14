import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

const requiredText = (min, max) => z.string().trim().min(min).max(max);
// Empty optional inputs must not send invalid empty postal codes/emails to Java.
const optionalText = (schema) => z.preprocess(
  (value) => typeof value === 'string' && value.trim() === '' ? undefined : value,
  schema.optional(),
);

// Mirror common-core PhoneNumberNormalizer (FR-PAR-005 / FR-AUTH-001).
const phone = z.string().transform((raw) => {
  const compact = raw.trim().replace(/[\s\-().[\]]/g, '');
  if (compact.startsWith('+94')) return compact;
  if (compact.startsWith('94')) return `+${compact}`;
  if (compact.startsWith('0')) return `+94${compact.slice(1)}`;
  return compact;
}).pipe(z.string().regex(/^\+94[1-9][0-9]{8}$/, 'Enter a valid Sri Lankan phone number'));

const decimalText = z.union([z.string(), z.number().finite()])
  .transform((value) => String(value).trim())
  .pipe(z.string().regex(/^\d+(?:\.\d{1,2})?$/, 'Use a decimal with at most two decimal places'));

// Integer hundredths avoid rounding and preserve the submitted decimal text.
const hundredths = (text) => {
  const [whole, fraction = ''] = text.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
};

/** FR-UI-004 / BR-10: build only after GET /public/districts succeeds.
 * No default maximum or hard-coded size/district list is used.
 * The output retains weight text (e.g. "1.20") for JSON BigDecimal binding.
 */
export function createDeliverySchema(referenceData) {
  const max = decimalText.safeParse(referenceData?.maxWeightKg);
  if (!max.success || hundredths(max.data) < 1n ||
      !Array.isArray(referenceData?.sizes) || referenceData.sizes.length === 0 ||
      !Array.isArray(referenceData?.districts) || referenceData.districts.length === 0) {
    throw new Error('Delivery reference data must be loaded before validating the form');
  }
  const sizeCodes = new Set(referenceData.sizes.map(({ size }) => size));
  const districtCodes = new Set(referenceData.districts.map(({ code }) => code));
  const address = {
    contactName: requiredText(2, 100),
    contactPhone: phone,
    addressLine1: requiredText(1, 150),
    addressLine2: optionalText(z.string().trim().max(150)),
    city: requiredText(1, 80),
    districtCode: z.string().refine((code) => districtCodes.has(code), 'Select an available district'),
    postalCode: optionalText(z.string().trim().regex(/^[0-9]{5}$/, 'Enter a five-digit postal code')),
  };
  return z.object({
    pickup: z.object(address),
    dropoff: z.object({
      ...address,
      contactEmail: optionalText(z.string().trim().max(254).email('Enter a valid email address')),
    }),
    parcel: z.object({
      weightKg: decimalText.superRefine((value, ctx) => {
        // A format error already has its own issue; never parse malformed text.
        if (!/^\d+(?:\.\d{1,2})?$/.test(value)) return;
        if (hundredths(value) < 1n) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Weight must be at least 0.01 kg' });
        }
        if (hundredths(value) > hundredths(max.data)) {
          ctx.addIssue({ code: z.ZodIssueCode.custom, message: `Weight must not exceed ${max.data} kg` });
        }
      }),
      size: z.string().refine((size) => sizeCodes.has(size), 'Select an available parcel size'),
      description: optionalText(z.string().max(200)),
    }),
    deliveryInstructions: optionalText(z.string().max(300)),
  });
}

/** Pass as useForm({ resolver: createDeliveryResolver(referenceData) }).
 * expectedFee is attached from the quote at confirmation, never calculated here.
 */
export function createDeliveryResolver(referenceData) {
  return zodResolver(createDeliverySchema(referenceData));
}
