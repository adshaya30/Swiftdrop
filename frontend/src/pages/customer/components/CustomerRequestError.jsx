import React from 'react';
import ErrorState from '../../../components/ErrorState';

export default function CustomerRequestError({ error, onRetry }) {
  const message = error?.status === 401
    ? 'Your session could not be verified. Please sign in again.'
    : error?.status === 403
      ? 'You do not have permission to view customer parcels.'
      : 'We could not load your parcels. Please try again.';
  // Do not display raw response bodies or replace the shared authentication flow.
  return <div role="alert"><ErrorState message={message} onRetry={onRetry} /></div>;
}
