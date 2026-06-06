import { ArrowLeft } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import type { User } from "../../../types/auth.types";

interface MediaSubNavProps {
  user: User;
  /** Current page label shown after "Media Library" in the breadcrumb. */
  page: string;
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
 * Shared top navigation for Media Library sub-pages (Trash, Duplicate Review, Repository Health).
 * Reuses the Media Library's top-nav chrome so the pages aren't bare: back button, logo, and a
 * "Media Library > {page}" breadcrumb.
 */
export default function MediaSubNav({ user, page }: MediaSubNavProps) {
  const navigate = useNavigate();
  return (
    <nav className="med-topnav">
      <div className="med-nav-left">
        <button className="med-back-btn" type="button" onClick={() => navigate("/media-repository")}>
          <ArrowLeft size={16} aria-hidden="true" />
          <span>Back</span>
        </button>
        <div className="med-nav-brand">
          <div className="med-nav-brand-icon">
            <BrandMark />
          </div>
          <div className="med-nav-brand-name">
            DASIG<em>Connect</em>
          </div>
        </div>
        <div className="med-nav-breadcrumb" aria-label="Breadcrumb">
          <span className="med-nav-chevron">&gt;</span>
          <Link to="/media-repository" className="med-nav-crumb-link">Media Library</Link>
          <span className="med-nav-chevron">&gt;</span>
          <span aria-current="page">{page}</span>
        </div>
      </div>
      <div className="med-nav-right">
        <div className="med-nav-chip">{formatRole(user.role)}</div>
        <div className="med-nav-avatar" aria-label={user.email}>
          {user.initials}
        </div>
      </div>
    </nav>
  );
}
