import { useState } from 'react';
import {
  Navigation,
  CheckCircle2,
  Clock,
  Phone,
  Home,
  Map,
  History,
  User,
  ArrowRight,
  ChevronRight,
} from 'lucide-react';
import SwiftDropLogo from '../components/SwiftDropLogo';
import { useAuth } from '../context/AuthContext';
import './DashboardPages.css';

export default function CourierDashboardPage() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<'home' | 'map' | 'history' | 'profile'>('home');
  const [arrived, setArrived] = useState(false);

  const courierFirstName = user?.fullName?.split(' ')[0] || 'Tharindu';

  return (
    <div className="sd-courier-screen-container">
      {/* Mobile Device Frame */}
      <div className="sd-mobile-frame">
        {/* Device Notch / Status Bar */}
        <div className="sd-mobile-notch-bar">
          <span className="sd-notch-time">9:41</span>
          <div className="sd-notch-speaker" />
          <span className="sd-notch-icons">5G 100%</span>
        </div>

        {/* Top Header Bar (SwiftDrop Orange Header) */}
        <div className="sd-courier-topbar">
          <SwiftDropLogo variant="light" size="sm" />
          <div className="sd-courier-badge">Active Shift</div>
        </div>

        {/* Screen Content based on Active Tab */}
        <div className="sd-courier-scroll-body">
          {activeTab === 'home' && (
            <div className="sd-courier-home-tab">
              {/* Courier Greeting */}
              <div className="sd-courier-greeting-card">
                <h2 className="sd-courier-hello">Hello, {courierFirstName}!</h2>
                <p className="sd-courier-sub">
                  Today&apos;s Deliveries: <strong>5</strong> &bull; Completed: <strong>2</strong>
                </p>
              </div>

              {/* Next Delivery Card (#SD-2026-00128) */}
              <div
                className="sd-courier-next-card"
                onClick={() => setActiveTab('map')}
                role="button"
                tabIndex={0}
              >
                <div className="sd-next-header">
                  <div>
                    <span className="sd-next-badge">Next Delivery</span>
                    <h3 className="sd-next-id">#SD-2026-00128</h3>
                  </div>
                  <ChevronRight size={22} color="#EA580C" />
                </div>
                <p className="sd-next-addr">123 Galle Road, Colombo 03</p>
                <div className="sd-next-eta">
                  <Clock size={14} /> Estimated Arrival: 15 mins
                </div>
              </div>

              {/* Delivery Milestone Stepper */}
              <div className="sd-courier-stepper-card">
                <h4 className="sd-stepper-title">Current Milestone</h4>
                <div className="sd-mobile-timeline">
                  <div className="sd-m-step completed">
                    <span className="sd-m-step-icon"><CheckCircle2 size={16} /></span>
                    <div className="sd-m-step-info">
                      <span className="sd-m-step-name">Pickup Complete</span>
                      <span className="sd-m-step-time">09:15 AM &bull; Central Hub</span>
                    </div>
                  </div>

                  <div className="sd-m-step active">
                    <span className="sd-m-step-icon"><Navigation size={16} /></span>
                    <div className="sd-m-step-info">
                      <span className="sd-m-step-name">On the Way</span>
                      <span className="sd-m-step-time">In Transit via Galle Road</span>
                    </div>
                  </div>

                  <div className="sd-m-step pending">
                    <span className="sd-m-step-icon">3</span>
                    <div className="sd-m-step-info">
                      <span className="sd-m-step-name">Out for Delivery</span>
                      <span className="sd-m-step-time">Recipient Verification</span>
                    </div>
                  </div>

                  <div className="sd-m-step pending">
                    <span className="sd-m-step-icon">4</span>
                    <div className="sd-m-step-info">
                      <span className="sd-m-step-name">Delivered</span>
                      <span className="sd-m-step-time">Digital Signature / PIN</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Quick Action Button to Map */}
              <button
                type="button"
                className="sd-btn sd-btn-primary sd-btn-full sd-courier-action-btn"
                onClick={() => setActiveTab('map')}
              >
                Start Navigation <ArrowRight size={16} />
              </button>
            </div>
          )}

          {activeTab === 'map' && (
            <div className="sd-courier-map-tab">
              <div className="sd-map-top-card">
                <span className="sd-next-badge">Waypoint Route</span>
                <h3 className="sd-next-id">#SD-2026-00128</h3>
              </div>

              {/* Interactive Vector Map Graphic */}
              <div className="sd-courier-map-view">
                <svg viewBox="0 0 320 240" fill="none" xmlns="http://www.w3.org/2000/svg" className="sd-route-svg">
                  {/* Map Terrain background */}
                  <rect width="320" height="240" fill="#E2E8F0" />
                  {/* Street grid */}
                  <path d="M 0 50 L 320 50 M 0 110 L 320 110 M 0 170 L 320 170" stroke="#CBD5E1" strokeWidth="6" />
                  <path d="M 60 0 L 60 240 M 160 0 L 160 240 M 260 0 L 260 240" stroke="#CBD5E1" strokeWidth="6" />

                  {/* Ocean coastline (Colombo coastal road) */}
                  <path d="M 0 0 L 35 0 L 45 240 L 0 240 Z" fill="#BAE6FD" />

                  {/* Galle Road Main Route (Orange Highlight) */}
                  <path
                    d="M 60 210 L 60 110 L 160 110 L 160 60 L 260 60"
                    stroke="#F97316"
                    strokeWidth="8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />

                  {/* Vehicle Origin Pin */}
                  <circle cx="60" cy="210" r="10" fill="#172033" />
                  <circle cx="60" cy="210" r="4" fill="#FFFFFF" />

                  {/* Courier Vehicle Marker */}
                  <g transform="translate(145, 95)">
                    <rect x="0" y="0" width="30" height="30" rx="8" fill="#EA580C" stroke="#FFFFFF" strokeWidth="2" />
                    <text x="15" y="20" fill="#FFFFFF" fontSize="14" textAnchor="middle">🛵</text>
                  </g>

                  {/* Destination Pin */}
                  <g transform="translate(250, 40)">
                    <circle cx="10" cy="20" r="12" fill="#16A34A" stroke="#FFFFFF" strokeWidth="2" />
                    <circle cx="10" cy="20" r="4" fill="#FFFFFF" />
                  </g>
                </svg>
              </div>

              {/* Waypoint Destination Card */}
              <div className="sd-courier-dest-card">
                <div className="sd-dest-row">
                  <div>
                    <span className="sd-dest-to">Destination</span>
                    <h4 className="sd-dest-address">123 Galle Road, Colombo 03</h4>
                    <span className="sd-dest-recipient">Recipient: Kamal Jayawardena</span>
                  </div>
                  <a href="tel:0771234567" className="sd-icon-btn sd-call-btn" aria-label="Call Recipient">
                    <Phone size={18} color="#16A34A" />
                  </a>
                </div>

                <button
                  type="button"
                  className={`sd-btn ${arrived ? 'sd-btn-secondary' : 'sd-btn-primary'} sd-btn-full sd-arrive-btn`}
                  onClick={() => setArrived(prev => !prev)}
                >
                  {arrived ? '✓ Arrived — Verify OTP / Signature' : 'Arrived at Location'}
                </button>
              </div>
            </div>
          )}

          {activeTab === 'history' && (
            <div className="sd-courier-history-tab">
              <h3 className="sd-tab-heading">Today&apos;s Run History</h3>
              <div className="sd-history-item">
                <div className="sd-hist-status delivered">Delivered</div>
                <div className="sd-hist-details">
                  <span className="sd-hist-id">#SD-2026-00124</span>
                  <span className="sd-hist-loc">45 Dharmapala Mw, Colombo 07</span>
                  <span className="sd-hist-time">09:45 AM &bull; Recipient OTP</span>
                </div>
              </div>
              <div className="sd-history-item">
                <div className="sd-hist-status delivered">Delivered</div>
                <div className="sd-hist-details">
                  <span className="sd-hist-id">#SD-2026-00121</span>
                  <span className="sd-hist-loc">12 Duplication Rd, Colombo 04</span>
                  <span className="sd-hist-time">09:12 AM &bull; Signature</span>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'profile' && (
            <div className="sd-courier-profile-tab">
              <div className="sd-profile-card-mini">
                <div className="sd-courier-avatar-large">
                  {courierFirstName.charAt(0)}
                </div>
                <h3>{user?.fullName || 'Tharindu Perera'}</h3>
                <span className="sd-courier-role-tag">Courier &bull; Western Metro Hub</span>
                <p className="sd-courier-email">{user?.email}</p>
              </div>
            </div>
          )}
        </div>

        {/* Mobile Bottom Navigation Bar (Home, Map, History, Profile) */}
        <div className="sd-mobile-tab-bar" role="tablist">
          <button
            type="button"
            className={`sd-tab-btn ${activeTab === 'home' ? 'active' : ''}`}
            onClick={() => setActiveTab('home')}
            role="tab"
            aria-selected={activeTab === 'home'}
          >
            <Home size={20} />
            <span>Home</span>
          </button>

          <button
            type="button"
            className={`sd-tab-btn ${activeTab === 'map' ? 'active' : ''}`}
            onClick={() => setActiveTab('map')}
            role="tab"
            aria-selected={activeTab === 'map'}
          >
            <Map size={20} />
            <span>Map</span>
          </button>

          <button
            type="button"
            className={`sd-tab-btn ${activeTab === 'history' ? 'active' : ''}`}
            onClick={() => setActiveTab('history')}
            role="tab"
            aria-selected={activeTab === 'history'}
          >
            <History size={20} />
            <span>History</span>
          </button>

          <button
            type="button"
            className={`sd-tab-btn ${activeTab === 'profile' ? 'active' : ''}`}
            onClick={() => setActiveTab('profile')}
            role="tab"
            aria-selected={activeTab === 'profile'}
          >
            <User size={20} />
            <span>Profile</span>
          </button>
        </div>
      </div>
    </div>
  );
}
