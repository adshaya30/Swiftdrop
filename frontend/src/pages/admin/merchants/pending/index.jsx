import React from 'react';
import MerchantDirectory from '../MerchantDirectory';

/** Route /admin/merchants/pending (ADMIN): merchants waiting for approval. SRS v2.1 - FR-AUTH-017, UC-A05. */
export default function PendingMerchantsPage() {
  return (
    <MerchantDirectory
      lockedStatus="PENDING"
      title="Pending merchants"
      subtitle="Registrations waiting for your decision. Open a merchant to approve or reject it."
    />
  );
}
