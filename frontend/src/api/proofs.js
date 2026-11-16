import { apiRequest, apiBlob } from './http';

/**
 * Proof of delivery API client (SRS FR-DEL-007, BR-06).
 */

/**
 * Get proof of delivery metadata (recipientName, note, hasPhoto, capturedAt).
 * @param {string} parcelId - UUID of the parcel
 */
export async function getProof(parcelId) {
  return apiRequest('GET', `/api/v1/proofs/${parcelId}`);
}

/**
 * Get delivery proof photo binary as a Blob.
 * Uses Bearer authentication via apiBlob.
 * @param {string} parcelId - UUID of the parcel
 * @returns {Promise<Blob>}
 */
export async function getProofPhoto(parcelId) {
  return apiBlob(`/api/v1/proofs/${parcelId}/photo`);
}

