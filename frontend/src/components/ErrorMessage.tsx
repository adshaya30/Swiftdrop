import './ErrorMessage.css';

interface ErrorMessageProps {
  title?: string;
  detail: string;
  fieldErrors?: Record<string, string>;
  traceId?: string;
  onRetry?: () => void;
  onDismiss?: () => void;
}

export default function ErrorMessage({
  title = 'An error occurred',
  detail,
  fieldErrors,
  traceId,
  onRetry,
  onDismiss,
}: ErrorMessageProps) {
  const hasFieldErrors = fieldErrors && Object.keys(fieldErrors).length > 0;

  return (
    <div role="alert" aria-live="assertive" className="sd-error-card">
      <div className="sd-error-header">
        <span className="sd-error-icon" aria-hidden="true">⚠️</span>
        <div className="sd-error-text">
          <strong className="sd-error-title">{title}</strong>
          <p className="sd-error-detail">{detail}</p>
        </div>
        {onDismiss && (
          <button
            type="button"
            className="sd-error-dismiss"
            onClick={onDismiss}
            aria-label="Dismiss alert"
          >
            &times;
          </button>
        )}
      </div>

      {hasFieldErrors && (
        <ul className="sd-error-fields">
          {Object.entries(fieldErrors).map(([field, msg]) => (
            <li key={field}>
              <strong>{field}:</strong> {msg}
            </li>
          ))}
        </ul>
      )}

      {(onRetry || traceId) && (
        <div className="sd-error-footer">
          {traceId && <span className="sd-error-trace">Ref: {traceId}</span>}
          {onRetry && (
            <button type="button" className="sd-error-retry-btn" onClick={onRetry}>
              Try Again
            </button>
          )}
        </div>
      )}
    </div>
  );
}

