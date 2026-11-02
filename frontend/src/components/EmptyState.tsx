import type { ReactNode } from 'react';
import './EmptyState.css';

interface EmptyStateProps {
  title: string;
  description: string;
  icon?: string;
  action?: ReactNode;
}

export default function EmptyState({
  title,
  description,
  icon = '📦',
  action,
}: EmptyStateProps) {
  return (
    <div className="sd-empty-state" role="region" aria-label={title}>
      <div className="sd-empty-icon" aria-hidden="true">
        {icon}
      </div>
      <h3 className="sd-empty-title">{title}</h3>
      <p className="sd-empty-desc">{description}</p>
      {action && <div className="sd-empty-action">{action}</div>}
    </div>
  );
}
