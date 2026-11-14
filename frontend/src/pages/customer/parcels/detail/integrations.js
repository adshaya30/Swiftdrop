import ProofViewer from '../../../courier/components/ProofViewer';
import ReceiptButton from '../../../merchant/components/ReceiptButton';

// Master 8.1: only confirmed owner contracts are enabled. Swap these adapters
// after #9 publishes getTimeline/ParcelTimeline.
export const ownerIntegrations = {
  ProofViewer,
  getTimeline: null,
  ParcelTimeline: null,
  ReceiptButton,
};

// #9's existing public hook ignores {onStatus}. Do not open an ineffective
// subscription or duplicate its SSE/polling implementation. Replace this thin
// adapter with the owner's hook when its callback contract has been verified.
export function useCustomerStatusStream() {}
export const hasCustomerStatusStream = false;
