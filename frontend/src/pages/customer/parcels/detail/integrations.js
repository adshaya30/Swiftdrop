import ProofViewer from '../../../courier/components/ProofViewer';

// Master 8.1: only confirmed owner contracts are enabled. Swap these adapters
// after #9 publishes getTimeline/ParcelTimeline and #6 publishes ReceiptButton.
export const ownerIntegrations = {
  ProofViewer,
  getTimeline: null,
  ParcelTimeline: null,
  ReceiptButton: null,
};

// #9's existing public hook ignores {onStatus}. Do not open an ineffective
// subscription or duplicate its SSE/polling implementation. Replace this thin
// adapter with the owner's hook when its callback contract has been verified.
export function useCustomerStatusStream() {}
export const hasCustomerStatusStream = false;
