import { Link } from 'react-router-dom';
import { Truck, CheckCircle, Users, Clock, ArrowUpRight, ArrowRight } from 'lucide-react';
import './DashboardPages.css';

export default function AdminDashboardPage() {
  const couriers = [
    { name: 'Ahamed', deliveries: 12, max: 15 },
    { name: 'Nimal', deliveries: 10, max: 15 },
    { name: 'Rashmi', deliveries: 9, max: 15 },
    { name: 'Tharindu', deliveries: 8, max: 15 },
  ];

  return (
    <div className="sd-dashboard-page">
      {/* ------------------------------------------------------------------ */}
      {/* KPI METRIC CARDS (Matches Operations Dashboard in Reference Image) */}
      {/* ------------------------------------------------------------------ */}
      <div className="sd-kpi-grid">
        {/* Total Deliveries */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Total Deliveries</span>
            <div className="sd-kpi-icon-box green">
              <Truck size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">1,250</span>
            <span className="sd-trend-badge positive">
              <ArrowUpRight size={14} /> +12%
            </span>
          </div>
          <span className="sd-kpi-subtext">Compared to last week</span>
        </div>

        {/* Success Rate */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Success Rate</span>
            <div className="sd-kpi-icon-box green">
              <CheckCircle size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">97.2%</span>
            <span className="sd-trend-badge positive">
              <ArrowUpRight size={14} /> +0.5%
            </span>
          </div>
          <span className="sd-kpi-subtext">First-attempt completion</span>
        </div>

        {/* Active Couriers */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Active Couriers</span>
            <div className="sd-kpi-icon-box orange">
              <Users size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">48</span>
            <span className="sd-kpi-badge neutral">Online</span>
          </div>
          <span className="sd-kpi-subtext">Across 9 provinces</span>
        </div>

        {/* Pending Assignments */}
        <div className="sd-card sd-kpi-card">
          <div className="sd-kpi-header">
            <span className="sd-kpi-label">Pending Assignments</span>
            <div className="sd-kpi-icon-box red">
              <Clock size={20} />
            </div>
          </div>
          <div className="sd-kpi-value-row">
            <span className="sd-kpi-val">27</span>
            <span className="sd-kpi-badge warning">Needs Dispatch</span>
          </div>
          <span className="sd-kpi-subtext">Average dispatch: 4.2 min</span>
        </div>
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Deliveries Trend Chart & Courier Workload Grid                      */}
      {/* ------------------------------------------------------------------ */}
      <div className="sd-dashboard-grid">
        {/* Deliveries Trend Chart Card */}
        <div className="sd-card sd-chart-card">
          <div className="sd-chart-header">
            <div>
              <h3 className="sd-chart-title">Deliveries Trend</h3>
              <p className="sd-chart-subtitle">Daily parcel dispatch volume across Sri Lanka</p>
            </div>
            <span className="sd-timeframe-tag">This Week</span>
          </div>

          <div className="sd-chart-canvas-wrapper">
            <svg viewBox="0 0 540 200" fill="none" xmlns="http://www.w3.org/2000/svg" className="sd-trend-svg">
              {/* Horizontal Grid lines and Y-axis labels */}
              <text x="15" y="30" fill="#94A3B8" fontSize="10">1.5k</text>
              <line x1="45" y1="26" x2="520" y2="26" stroke="#F1F5F9" strokeWidth="1" />

              <text x="15" y="75" fill="#94A3B8" fontSize="10">1k</text>
              <line x1="45" y1="71" x2="520" y2="71" stroke="#F1F5F9" strokeWidth="1" />

              <text x="15" y="120" fill="#94A3B8" fontSize="10">500</text>
              <line x1="45" y1="116" x2="520" y2="116" stroke="#F1F5F9" strokeWidth="1" />

              <text x="25" y="165" fill="#94A3B8" fontSize="10">0</text>
              <line x1="45" y1="161" x2="520" y2="161" stroke="#E2E8F0" strokeWidth="1" />

              {/* Area Gradient */}
              <defs>
                <linearGradient id="trendGradient" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#F97316" stopOpacity="0.28" />
                  <stop offset="100%" stopColor="#F97316" stopOpacity="0.0" />
                </linearGradient>
              </defs>

              {/* Smooth trend curve area */}
              <path
                d="M 60 140
                   C 100 135, 120 150, 150 130
                   C 190 100, 220 105, 270 95
                   C 320 85, 360 88, 410 65
                   C 460 72, 480 60, 510 62
                   L 510 161 L 60 161 Z"
                fill="url(#trendGradient)"
              />

              {/* Trend line */}
              <path
                d="M 60 140
                   C 100 135, 120 150, 150 130
                   C 190 100, 220 105, 270 95
                   C 320 85, 360 88, 410 65
                   C 460 72, 480 60, 510 62"
                stroke="#F97316"
                strokeWidth="3"
                strokeLinecap="round"
              />

              {/* Data points */}
              <circle cx="60" cy="140" r="4" fill="#F97316" stroke="#FFFFFF" strokeWidth="2" />
              <circle cx="150" cy="130" r="4" fill="#F97316" stroke="#FFFFFF" strokeWidth="2" />
              <circle cx="270" cy="95" r="4" fill="#F97316" stroke="#FFFFFF" strokeWidth="2" />
              <circle cx="410" cy="65" r="4" fill="#F97316" stroke="#FFFFFF" strokeWidth="2" />
              <circle cx="510" cy="62" r="5" fill="#EA580C" stroke="#FFFFFF" strokeWidth="2" />

              {/* X Axis Labels */}
              <text x="60" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Mon</text>
              <text x="135" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Tue</text>
              <text x="210" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Wed</text>
              <text x="285" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Thu</text>
              <text x="360" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Fri</text>
              <text x="435" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Sat</text>
              <text x="510" y="182" fill="#64748B" fontSize="11" textAnchor="middle">Sun</text>
            </svg>
          </div>
        </div>

        {/* Courier Workload Card */}
        <div className="sd-card sd-workload-card">
          <div className="sd-workload-header">
            <div>
              <h3 className="sd-chart-title">Courier Workload</h3>
              <p className="sd-chart-subtitle">Active dispatch distribution today</p>
            </div>
            <Link to="/admin/users" className="sd-workload-link">
              Users &rarr;
            </Link>
          </div>

          <div className="sd-workload-list">
            {couriers.map(c => {
              const pct = Math.round((c.deliveries / c.max) * 100);
              return (
                <div key={c.name} className="sd-workload-row">
                  <div className="sd-workload-info">
                    <span className="sd-courier-name">{c.name}</span>
                    <span className="sd-courier-count">{c.deliveries}</span>
                  </div>
                  <div className="sd-workload-bar-track">
                    <div
                      className="sd-workload-bar-fill"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <div className="sd-workload-footer">
            <Link to="/admin/users" className="sd-btn sd-btn-secondary sd-btn-full">
              Manage System Users <ArrowRight size={16} />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
