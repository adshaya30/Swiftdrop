/** SRS 6.2 values used by the admin parcel pages. Labels for statuses come from the shared StatusChip tokens. */
export const PARCEL_STATUSES = [
  'CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED',
];

export const OWNER_TYPES = [
  { value: 'CUSTOMER', label: 'Customer' },
  { value: 'MERCHANT', label: 'Merchant' },
];

/** SRS 6.2 failure reason labels. */
export const FAILURE_REASON_LABELS = {
  RECIPIENT_UNAVAILABLE: 'Recipient not available',
  WRONG_OR_INCOMPLETE_ADDRESS: 'Wrong or incomplete address',
  RECIPIENT_REFUSED: 'Recipient refused the parcel',
  SENDER_UNAVAILABLE_AT_PICKUP: 'Sender not available at pickup',
  PARCEL_DAMAGED: 'Parcel damaged',
  ACCESS_RESTRICTED: 'Location not accessible',
  OTHER: 'Other reason',
};

/** FR-PAR-012, Table 2.4: the admin override offers only these reasons. */
export const ADMIN_FAIL_REASONS = ['PARCEL_DAMAGED', 'OTHER'];

/** FR-PAR-011: admin cancel only from CREATED. FR-PAR-012: admin fail only after pickup. */
export const ADMIN_CANCEL_FROM = ['CREATED'];
export const ADMIN_FAIL_FROM = ['PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY'];

export const TERMINAL_STATUSES = ['DELIVERED', 'FAILED', 'CANCELLED'];
