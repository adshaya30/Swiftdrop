import { Link } from 'react-router-dom';

export interface SwiftDropLogoProps {
  /**
   * Theme variant:
   * - 'dark' (default): For light backgrounds (Navy "SwiftDrop" + Orange box)
   * - 'light': For dark navy sidebar/header (White "SwiftDrop" + Orange box)
   * - 'icon-only': Only the orange parcel box with orbit ring & speed streaks
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
  const heightMap = {
    sm: 28,
    md: 38,
    lg: 52,
  }[size];

  // Select the appropriate transparent PNG asset
  let src = '/logo.png';
  let alt = 'SwiftDrop';

  if (variant === 'light') {
    src = '/logo-white.png';
  } else if (variant === 'icon-only') {
    src = '/logo-icon.png';
    alt = 'SwiftDrop Icon';
  }

  const logoImg = (
    <img
      src={src}
      alt={alt}
      height={heightMap}
      className={`sd-logo-img ${className}`}
      style={{
        height: `${heightMap}px`,
        width: 'auto',
        display: 'block',
        objectFit: 'contain',
        userSelect: 'none',
      }}
      loading="eager"
    />
  );

  if (asLink) {
    return (
      <Link
        to={to}
        style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}
        aria-label="SwiftDrop Home"
      >
        {logoImg}
      </Link>
    );
  }

  return logoImg;
}

export default SwiftDropLogo;
