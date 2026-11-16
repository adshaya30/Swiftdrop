import { useEffect, useState } from 'react';

/** Clear old data on query changes/errors; never publish an obsolete response. */
export function useCustomerRequest(request, key, revision) {
  const [result, setResult] = useState({});
  useEffect(() => {
    let current = true;
    Promise.resolve().then(request).then(
      (data) => { if (current) setResult({ key, revision, data, status: 'success' }); },
      (error) => { if (current) setResult({ key, revision, error, status: 'error' }); },
    );
    return () => { current = false; };
  }, [request, key, revision]);
  return result.key === key && result.revision === revision ? result : { status: 'loading' };
}
