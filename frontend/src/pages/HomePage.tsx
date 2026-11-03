import { Link } from 'react-router-dom';
import { ArrowRight, ShieldCheck, Zap, Globe, Building2, Truck, CheckCircle } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { getRoleHomePath } from '../utils/roleUtils';
import TrackingViewer from '../components/TrackingViewer';
import './HomePage.css';

export default function HomePage() {
  const { user, isAuthenticated } = useAuth();
  const roleHome = getRoleHomePath(user?.role);

  return (
    <div className="sd-landing-page">
      {/* ------------------------------------------------------------------ */}
      {/* HERO SECTION (Matches Top-Left of Reference Image)                 */}
      {/* ------------------------------------------------------------------ */}
      <section className="sd-hero-section">
        <div className="sd-hero-container">
          <div className="sd-hero-content">
            <div className="sd-hero-badge">
              <span className="sd-badge-dot" />
              <span>Island-wide Express Logistics Network</span>
            </div>

            <h1 className="sd-hero-title">
              Fast Delivery.
              <span className="sd-hero-title-highlight">Everywhere.</span>
            </h1>

            <p className="sd-hero-subtitle">
              Reliable courier service across the island. From express parcels in Colombo to bulk merchant freight in Kandy, Galle, and beyond.
            </p>

            <div className="sd-hero-cta-group">
              <a href="#track" className="sd-btn sd-btn-primary sd-hero-primary-btn">
                Track Your Parcel <ArrowRight size={18} />
              </a>

              {isAuthenticated ? (
                <Link to={roleHome} className="sd-btn sd-btn-secondary sd-hero-secondary-btn">
                  Go to {user?.role} Portal
                </Link>
              ) : (
                <Link to="/register" className="sd-btn sd-btn-secondary sd-hero-secondary-btn">
                  Create Delivery
                </Link>
              )}
            </div>

            <div className="sd-hero-trust-row">
              <div className="sd-trust-item">
                <CheckCircle size={16} color="#16A34A" />
                <span>99.4% On-Time Delivery</span>
              </div>
              <div className="sd-trust-item">
                <CheckCircle size={16} color="#16A34A" />
                <span>All 9 Provinces Covered</span>
              </div>
              <div className="sd-trust-item">
                <CheckCircle size={16} color="#16A34A" />
                <span>Real-Time GPS Tracking</span>
              </div>
            </div>
          </div>

          {/* Hero Visual Artwork (Courier & Delivery Van) */}
          <div className="sd-hero-visual" aria-hidden="true">
            <div className="sd-hero-visual-card">
              {/* Curved orange brand wave background */}
              <div className="sd-hero-curved-bg" />

              <svg viewBox="0 0 520 440" fill="none" xmlns="http://www.w3.org/2000/svg" className="sd-hero-illustration">
                {/* Background City Skyline Silhouette */}
                <rect x="60" y="160" width="40" height="120" rx="4" fill="#E2E8F0" opacity="0.6" />
                <rect x="110" y="130" width="55" height="150" rx="4" fill="#CBD5E1" opacity="0.5" />
                <rect x="180" y="170" width="50" height="110" rx="4" fill="#E2E8F0" opacity="0.6" />
                <rect x="360" y="150" width="60" height="130" rx="4" fill="#CBD5E1" opacity="0.4" />
                <rect x="430" y="180" width="50" height="100" rx="4" fill="#E2E8F0" opacity="0.5" />

                {/* Road surface */}
                <path d="M0 320 Q260 300 520 320 L520 440 L0 440 Z" fill="#1E293B" />
                <path d="M40 370 L100 370 M160 370 L220 370 M280 370 L340 370 M400 370 L460 370" stroke="#FBBF24" strokeWidth="4" strokeLinecap="round" strokeDasharray="16 16" />

                {/* Delivery Van (Orange & White SwiftDrop Van) */}
                <g transform="translate(190, 180)">
                  {/* Van Body */}
                  <rect x="40" y="50" width="180" height="80" rx="12" fill="#F8FAFC" stroke="#E2E8F0" strokeWidth="2" />
                  <path d="M160 50 L200 75 L220 75 L220 130 L160 130 Z" fill="#F97316" />
                  {/* Windshield */}
                  <path d="M165 55 L195 75 L165 75 Z" fill="#38BDF8" opacity="0.7" />
                  {/* SwiftDrop side logo on Van */}
                  <rect x="60" y="70" width="85" height="30" rx="6" fill="#FFF7ED" />
                  <text x="102" y="89" fill="#172033" fontSize="12" fontWeight="800" textAnchor="middle">Swift<tspan fill="#F97316">Drop</tspan></text>
                  {/* Wheels */}
                  <circle cx="80" cy="130" r="18" fill="#172033" />
                  <circle cx="80" cy="130" r="8" fill="#94A3B8" />
                  <circle cx="185" cy="130" r="18" fill="#172033" />
                  <circle cx="185" cy="130" r="8" fill="#94A3B8" />
                  {/* Headlights */}
                  <rect x="215" y="90" width="7" height="14" rx="3" fill="#FBBF24" />
                </g>

                {/* Friendly Courier Figure with Orange Cap & Box */}
                <g transform="translate(90, 140)">
                  {/* Shadow */}
                  <ellipse cx="65" cy="215" rx="45" ry="10" fill="rgba(0,0,0,0.18)" />

                  {/* Body & Legs */}
                  <rect x="48" y="140" width="14" height="65" rx="5" fill="#172033" />
                  <rect x="68" y="140" width="14" height="65" rx="5" fill="#172033" />
                  {/* Shoes */}
                  <rect x="42" y="198" width="22" height="10" rx="4" fill="#334155" />
                  <rect x="68" y="198" width="22" height="10" rx="4" fill="#334155" />

                  {/* Polo Shirt (SwiftDrop Brand Orange) */}
                  <path d="M40 70 L90 70 L95 145 L35 145 Z" fill="#F97316" />
                  {/* Navy Collar */}
                  <path d="M52 70 L65 85 L78 70 Z" fill="#172033" />

                  {/* Arms holding the parcel */}
                  <path d="M40 80 L25 105 L45 115" stroke="#F97316" strokeWidth="12" strokeLinecap="round" />
                  <path d="M90 80 L105 105 L85 115" stroke="#F97316" strokeWidth="12" strokeLinecap="round" />

                  {/* Parcel Box in Courier's Hands */}
                  <g transform="translate(25, 95)">
                    {/* Isometric Box */}
                    <path d="M40 5 L70 20 L40 35 L10 20 Z" fill="#FDBA74" />
                    <path d="M10 20 L40 35 V75 L10 60 Z" fill="#F97316" />
                    <path d="M40 35 L70 20 V60 L40 75 Z" fill="#EA580C" />
                    {/* Tape */}
                    <path d="M25 12 L55 27" stroke="#FFF7ED" strokeWidth="3" />
                    <rect x="22" y="42" width="36" height="16" rx="3" fill="#FFFFFF" opacity="0.9" />
                    <text x="40" y="53" fill="#172033" fontSize="7" fontWeight="800" textAnchor="middle">Swift<tspan fill="#F97316">Drop</tspan></text>
                  </g>

                  {/* Head & Face */}
                  <circle cx="65" cy="45" r="16" fill="#FBCFE8" />
                  {/* Smile */}
                  <path d="M60 48 Q65 54 70 48" stroke="#172033" strokeWidth="1.5" strokeLinecap="round" />
                  {/* Eyes */}
                  <circle cx="59" cy="42" r="1.5" fill="#172033" />
                  <circle cx="71" cy="42" r="1.5" fill="#172033" />
                  {/* SwiftDrop Orange Cap */}
                  <path d="M48 40 Q65 24 82 40 L88 38 Q65 18 45 36 Z" fill="#F97316" />
                  <path d="M65 30 L88 36" stroke="#EA580C" strokeWidth="3" strokeLinecap="round" />
                </g>
              </svg>
            </div>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------------ */}
      {/* TRACKING SECTION (Matches Top-Right of Reference Image)            */}
      {/* ------------------------------------------------------------------ */}
      <section id="track" className="sd-tracking-section">
        <div className="sd-tracking-section-header">
          <span className="sd-section-badge">Live GPS Telemetry</span>
          <h2 className="sd-section-title">Track Your Parcel</h2>
          <p className="sd-section-subtitle">
            Enter your tracking number to get real-time milestone updates and live GPS waypoint tracking.
          </p>
        </div>

        <TrackingViewer initialCode="SD-2026-00125" />
      </section>

      {/* ------------------------------------------------------------------ */}
      {/* SERVICES / VALUE PROPOSITION SECTION                              */}
      {/* ------------------------------------------------------------------ */}
      <section id="services" className="sd-services-section">
        <div className="sd-section-header">
          <span className="sd-section-badge">Fast &amp; Reliable</span>
          <h2 className="sd-section-title">Why Sri Lanka Chooses SwiftDrop</h2>
          <p className="sd-section-subtitle">
            Engineered for modern commerce, express speed, and complete transparency at every waypoint.
          </p>
        </div>

        <div className="sd-services-grid">
          <div className="sd-service-card">
            <div className="sd-service-icon-box orange">
              <Zap size={24} />
            </div>
            <h3 className="sd-service-title">Express Same-Day Dispatch</h3>
            <p className="sd-service-desc">
              Guaranteed same-day pickups for metro Colombo and fast overnight delivery across all 9 provinces.
            </p>
          </div>

          <div className="sd-service-card">
            <div className="sd-service-icon-box blue">
              <Truck size={24} />
            </div>
            <h3 className="sd-service-title">Live Courier Waypoint Map</h3>
            <p className="sd-service-desc">
              Follow your courier on an interactive waypoint route from collection to your doorstep with live ETA.
            </p>
          </div>

          <div className="sd-service-card">
            <div className="sd-service-icon-box green">
              <ShieldCheck size={24} />
            </div>
            <h3 className="sd-service-title">Secure Verification &amp; COD</h3>
            <p className="sd-service-desc">
              Digital proof-of-delivery, recipient PIN verification, and automated COD bank settlements for merchants.
            </p>
          </div>

          <div className="sd-service-card">
            <div className="sd-service-icon-box purple">
              <Globe size={24} />
            </div>
            <h3 className="sd-service-title">All-Island Coverage</h3>
            <p className="sd-service-desc">
              Extensive logistics hubs across Western, Central, Southern, Northern, and Eastern provinces.
            </p>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------------ */}
      {/* FOR BUSINESS BANNER SECTION                                        */}
      {/* ------------------------------------------------------------------ */}
      <section id="business" className="sd-business-banner">
        <div className="sd-business-inner">
          <div className="sd-business-content">
            <div className="sd-business-tag">
              <Building2 size={16} />
              <span>For E-Commerce &amp; Retailers</span>
            </div>
            <h2 className="sd-business-title">Supercharge Your Merchant Deliveries</h2>
            <p className="sd-business-subtitle">
              Integrate SwiftDrop API, generate bulk waybills in seconds, access discounted courier rates, and view real-time operations dashboards.
            </p>
            <div className="sd-business-actions">
              <Link to="/register" className="sd-btn sd-btn-primary">
                Open Merchant Account <ArrowRight size={16} />
              </Link>
              <Link to="/login" className="sd-btn sd-btn-secondary">
                Merchant Sign In
              </Link>
            </div>
          </div>

          <div className="sd-business-metrics">
            <div className="sd-biz-stat">
              <span className="sd-biz-num">100K+</span>
              <span className="sd-biz-lbl">Monthly Parcels</span>
            </div>
            <div className="sd-biz-stat">
              <span className="sd-biz-num">2,400+</span>
              <span className="sd-biz-lbl">Active Merchants</span>
            </div>
            <div className="sd-biz-stat">
              <span className="sd-biz-num">99.4%</span>
              <span className="sd-biz-lbl">Fulfillment Rate</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
