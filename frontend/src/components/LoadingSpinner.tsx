import type { CSSProperties } from 'react';
import './LoadingSpinner.css';

interface LoadingSpinnerProps {
  label?: string;
  size?: 'sm' | 'md' | 'lg';
  fullScreen?: boolean;
  style?: CSSProperties;
}

export default function LoadingSpinner({
  label = 'Loading…',
  size = 'md',
  fullScreen = false,
  style,
}: LoadingSpinnerProps) {
  const content = (
    <div
      role="status"
      aria-live="polite"
      className={`sd-spinner-container sd-spinner-${size}`}
      style={style}
    >
      <div className="sd-spinner-circle" aria-hidden="true" />
      {label && <span className="sd-spinner-label">{label}</span>}
    </div>
  );

  if (fullScreen) {
    return <div className="sd-spinner-fullscreen">{content}</div>;
  }

  return content;
}

