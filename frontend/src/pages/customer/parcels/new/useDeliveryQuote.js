import { useEffect, useState } from 'react';
import { postQuote } from '../../../../api/quotes';

/** FR-UI-004: obsolete requests cannot overwrite a newer quotation. */
export function useDeliveryQuote(details, enabled, revision) {
  const [result, setResult] = useState({ status: 'idle' });
  const input = details ? JSON.stringify({
    originDistrictCode: details.pickup.districtCode,
    destinationDistrictCode: details.dropoff.districtCode,
    weightKg: details.parcel.weightKg,
    size: details.parcel.size,
  }) : null;

  useEffect(() => {
    let current = true;
    if (!enabled || !input) return undefined;
    setResult({ status: 'loading', input, revision });
    const timer = setTimeout(async () => {
      try {
        const fee = await postQuote(JSON.parse(input));
        if (current) setResult({ status: 'success', fee, input, revision });
      } catch (error) {
        if (current) setResult({ status: 'error', error, input, revision });
      }
    }, 400);
    return () => { current = false; clearTimeout(timer); };
  }, [input, enabled, revision]);

  // Invalidate synchronously, including the render before effect cleanup.
  if (!enabled || result.input !== input || result.revision !== revision) return { status: 'idle' };
  return result;
}
