import { Link } from 'react-router-dom';

export interface SwiftDropLogoProps {
  /**
   * Theme variant:
   * - 'dark' (default): For light backgrounds (Navy "Swift" + Orange "Drop")
   * - 'light': For dark navy sidebar/header (White "Swift" + Orange "Drop")
   * - 'icon-only': Only the isometric parcel box with speed lines
   */
  variant?: 'dark' | 'light' | 'icon-only';
  size?: 'sm' | 'md' | 'lg';
  asLink?: boolean;
  to?: string;
  className?: string;
}

export function SwiftDropLogo({
  variant = 'dark',
  size = 'md',
  asLink = false,
  to = '/',
  className = '',
}: SwiftDropLogoProps) {
  const sizeMap = {
    sm: { icon: 26, font: '1.05rem', gap: '0.45rem' },
    md: { icon: 34, font: '1.35rem', gap: '0.6rem' },
    lg: { icon: 44, font: '1.8rem', gap: '0.75rem' },
  }[size];

  const textColor = variant === 'light' ? '#FFFFFF' : '#172033';
  const dropColor = '#F97316';

  const logoGraphic = (
    <div
      className={`sd-logo-container ${className}`}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: sizeMap.gap,
        userSelect: 'none',
      }}
    >
      {/* 3D Isometric Parcel Mark with Speed Streaks */}
      <svg
        width={sizeMap.icon}
        height={sizeMap.icon}
        viewBox="0 0 48 48"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden="true"
        style={{ flexShrink: 0, filter: 'drop-shadow(0 2px 4px rgba(249, 115, 22, 0.25))' }}
      >
        <g transform="translate(4, 6)">
          {/* Motion speed streaks */}
          <path d="M0 10H6" stroke="#F97316" strokeWidth="2.5" strokeLinecap="round" />
          <path d="M2 18H8" stroke="#F97316" strokeWidth="2.5" strokeLinecap="round" />
          <path d="M0 26H5" stroke="#F97316" strokeWidth="2.5" strokeLinecap="round" />

          {/* Top Face (Warm bright orange) */}
          <path d="M24 2L37 9L24 16L11 9L24 2Z" fill="#FB923C" />
          <path d="M17.5 5.5L30.5 12.5" stroke="#FDBA74" strokeWidth="2" strokeLinecap="round" />

          {/* Left Face (Brand orange) */}
          <path d="M11 9L24 16V32L11 25V9Z" fill="#F97316" />
          <path d="M17.5 19.5V30" stroke="#EA580C" strokeWidth="1.5" strokeLinecap="round" />

          {/* Right Face (Deep orange shadow) */}
          <path d="M24 16L37 9V25L24 32V16Z" fill="#EA580C" />
          <path d="M30.5 12.5V28.5" stroke="#C2410C" strokeWidth="1.5" strokeLinecap="round" />
        </g>
      </svg>

      {variant !== 'icon-only' && (
        <span
          style={{
            fontFamily: "'Plus Jakarta Sans', var(--sd-font-heading, sans-serif)",
            fontWeight: 800,
            fontSize: sizeMap.font,
            letterSpacing: '-0.035em',
            lineHeight: 1,
            display: 'inline-flex',
            alignItems: 'baseline',
          }}
        >
          <span style={{ color: textColor }}>Swift</span>
          <span style={{ color: dropColor }}>Drop</span>
        </span>
      )}
    </div>
  );

  if (asLink) {
    return (
      <Link to={to} style={{ textDecoration: 'none', display: 'inline-block' }} aria-label="SwiftDrop Home">
        {logoGraphic}
      </Link>
    );
  }

  return logoGraphic;
}

export default SwiftDropLogo;
