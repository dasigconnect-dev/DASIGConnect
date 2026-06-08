import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import type { User } from "../../../types/auth.types";
// Owns the .alx-* chrome (top nav + analytics sub-sidebar). Imported here so a direct
// load of any analytics sub-page ships the stylesheet even without visiting Overview first.
import "../../../styles/analytics-layout.css";

interface AnalyticsNavItem {
  to: string;
  label: string;
  /** Tabler icon class, e.g. "ti ti-chart-infographic". */
  icon: string;
  /** Match the path exactly (used for the index route). */
  end?: boolean;
}

const ANALYTICS_NAV: AnalyticsNavItem[] = [
  { to: "/analytics", label: "Overview", icon: "ti ti-chart-infographic", end: true },
  { to: "/analytics/views", label: "Views", icon: "ti ti-eye" },
  { to: "/analytics/engagement", label: "Engagement", icon: "ti ti-message-circle" },
  { to: "/analytics/audience", label: "Audience", icon: "ti ti-users" },
];

interface AnalyticsLayoutProps {
  user: User;
  /** Current page label shown after "Analytics" in the breadcrumb and active in the sidebar. */
  page: string;
  children: ReactNode;
}

function BrandMark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
    </svg>
  );
}

function formatRole(role: User["role"]) {
  if (role === "admin") return "Administrator";
  return role.charAt(0).toUpperCase() + role.slice(1);
}

/**
 * Full-screen shell for the Analytics area, matching the Media Library sub-navigation pattern:
 * a sticky top nav (Back, brand, "Analytics > {page}" breadcrumb, role/avatar) plus a left
 * sub-sidebar that links between the analytics pages (Overview, Insights). Pages render their
 * own header/toolbar inside {children}.
 */
export default function AnalyticsLayout({ user, page, children }: AnalyticsLayoutProps) {
  const navigate = useNavigate();
  return (
    <div className="alx-shell" data-role={user.role}>
      <nav className="alx-topnav">
        <div className="alx-nav-left">
          <button className="alx-back-btn" type="button" onClick={() => navigate("/dashboard")}>
            <ArrowLeft size={16} aria-hidden="true" />
            <span>Back</span>
          </button>
          <div className="alx-nav-brand">
            <div className="alx-nav-brand-icon">
              <BrandMark />
            </div>
            <div className="alx-nav-brand-name">
              DASIG<em>Connect</em>
            </div>
          </div>
          <div className="alx-nav-breadcrumb" aria-label="Breadcrumb">
            <span className="alx-nav-chevron">&gt;</span>
            <Link to="/analytics" className="alx-nav-crumb-link">
              Analytics
            </Link>
            <span className="alx-nav-chevron">&gt;</span>
            <span aria-current="page">{page}</span>
          </div>
        </div>
        <div className="alx-nav-right">
          <div className="alx-nav-chip">{formatRole(user.role)}</div>
          <div className="alx-nav-avatar" aria-label={user.email}>
            {user.initials}
          </div>
        </div>
      </nav>

      <div className="alx-body">
        <aside className="alx-sidebar" aria-label="Analytics navigation">
          <span className="alx-sidebar-label">Analytics</span>
          <nav className="alx-sidebar-nav">
            {ANALYTICS_NAV.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  isActive ? "alx-sidebar-link active" : "alx-sidebar-link"
                }
              >
                <i className={item.icon} aria-hidden="true" />
                <span>{item.label}</span>
              </NavLink>
            ))}
          </nav>
        </aside>

        <main className="alx-content">{children}</main>
      </div>
    </div>
  );
}
