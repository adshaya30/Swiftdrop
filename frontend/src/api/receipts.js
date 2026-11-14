import { apiBlob } from './http';

/**
 * Delivery receipt (Member #6, FR-PAR-010, FR-UI-011). The PDF needs the bearer token, so it is always fetched
 * through the shared client as a Blob, never linked directly.
 * @param {string} parcelId
 * @returns {Promise<Blob>} application/pdf
 */
export async function downloadReceipt(parcelId) {
  return apiBlob(`/api/v1/parcels/${encodeURIComponent(parcelId)}/receipt`);
}

/** File name required by FR-PAR-010. */
export function receiptFileName(trackingNumber) {
  return `SwiftDrop-Receipt-${trackingNumber}.pdf`;
}

export default { downloadReceipt, receiptFileName };
