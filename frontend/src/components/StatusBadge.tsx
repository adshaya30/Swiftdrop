export type ParcelStatus =
  | 'CREATED'
  | 'PICKED_UP'
  | 'IN_TRANSIT'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED'
  | 'ACTIVE'
  | 'LOCKED'
  | 'DEACTIVATED';

interface StatusBadgeProps {
  status: ParcelStatus | string;
  size?: 'sm' | 'md';
  showDot?: boolean;
  className?: string;
}

export function StatusBadge({
  status,
  size = 'md',
  showDot = true,
  className = '',
}: StatusBadgeProps) {
  const normalized = status.toUpperCase().replace(/\s+/g, '_');

  // Colors aligned with PDF Page 2 & Reference Image
  const configMap: Record<
    string,
    { label: string; text: string; bg: string; dot: string }
  > = {
    CREATED: {
      label: 'Created',
      text: '#64748B',
      bg: '#F1F5F9',
      dot: '#94A3B8',
    },
    PICKED_UP: {
      label: 'Picked Up',
      text: '#2563EB',
      bg: '#EFF6FF',
      dot: '#3B82F6',
    },
    IN_TRANSIT: {
      label: 'In Transit',
      text: '#7C3AED',
      bg: '#F5F3FF',
      dot: '#8B5CF6',
    },
    OUT_FOR_DELIVERY: {
      label: 'Out for Delivery',
      text: '#EA580C',
      bg: '#FFF7ED',
      dot: '#F97316',
    },
    DELIVERED: {
      label: 'Delivered',
      text: '#16A34A',
      bg: '#F0FDF4',
      dot: '#22C55E',
    },
    FAILED: {
      label: 'Failed',
      text: '#DC2626',
      bg: '#FEF2F2',
      dot: '#EF4444',
    },
    ACTIVE: {
      label: 'Active',
      text: '#16A34A',
      bg: '#F0FDF4',
      dot: '#22C55E',
    },
    LOCKED: {
      label: 'Locked',
      text: '#D97706',
      bg: '#FEF3C7',
      dot: '#F59E0B',
    },
    DEACTIVATED: {
      label: 'Deactivated',
      text: '#DC2626',
      bg: '#FEF2F2',
      dot: '#EF4444',
    },
  };

  const current = configMap[normalized] || {
    label: status.replace(/_/g, ' '),
    text: '#64748B',
    bg: '#F1F5F9',
    dot: '#94A3B8',
  };

  const isSmall = size === 'sm';

  return (
    <span
      className={`sd-status-badge ${className}`}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: isSmall ? '0.25rem' : '0.375rem',
        padding: isSmall ? '0.15rem 0.5rem' : '0.25rem 0.65rem',
        fontSize: isSmall ? '0.7rem' : '0.75rem',
        fontWeight: 600,
        color: current.text,
        backgroundColor: current.bg,
        borderRadius: '9999px',
        lineHeight: 1.25,
        letterSpacing: '0.015em',
        border: `1px solid ${current.text}18`,
        whiteSpace: 'nowrap',
      }}
    >
      {showDot && (
        <span
          style={{
            width: isSmall ? '5px' : '5px',
            height: isSmall ? '5px' : '5px',
            borderRadius: '50%',
            backgroundColor: current.dot,
            display: 'inline-block',
          }}
          aria-hidden="true"
        />
      )}
      {current.label}
    </span>
  );
}

export default StatusBadge;
