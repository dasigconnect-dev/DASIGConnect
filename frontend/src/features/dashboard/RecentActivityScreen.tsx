import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import { listInstitutions } from "../../api/authApi";
import { listSubmissions, type SubmissionSummary } from "../../api/submissionApi";

interface RecentActivityScreenProps {
  user: User;
}

interface ActivityItem {
  id: string;
  title: string;
  subtitle: string;
  institution: string;
  submitted: string;
  rawStatus: string;
  status: {
    label: string;
    icon: string;
    className: string;
  };
}

export default function RecentActivityScreen({ user }: RecentActivityScreenProps) {
  const navigate = useNavigate();
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([]);
  const [institutions, setInstitutions] = useState<
    { id: string; name: string; code: string; emailDomain: string }[]
  >([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");

  useEffect(() => {
    // Only admins resolve the institution list; moderators are network-wide with
    // no owning institution, contributors get their name from the submission DTO.
    if (user?.role !== "admin") return;
    listInstitutions()
      .then((response) => {
        const mapped = response.data.map((item) => ({
          id: item.id,
          name: item.name,
          code: item.institutionCode,
          emailDomain: item.emailDomain,
        }));
        setInstitutions(mapped);
      })
      .catch(() => {
        setInstitutions([]);
      });
  }, [user?.role, user?.institutionId, user?.inst]);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    listSubmissions()
      .then((response) => {
        setSubmissions(response.data);
      })
      .catch(() => {
        setSubmissions([]);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [user?.role, user?.inst]);

  const allActivities: ActivityItem[] = useMemo(() => {
    return submissions
      .sort((a, b) => {
        const dateA = a.submittedAt ?? a.createdAt ?? "";
        const dateB = b.submittedAt ?? b.createdAt ?? "";
        return dateB.localeCompare(dateA);
      })
      .map((s) => {
        const institutionName =
          s.institutionName ||
          institutions.find((i) => i.id === s.institutionId)?.name ||
          getInstitutionName(user);

        const submitted = s.submittedAt ?? s.createdAt ?? "";
        const submittedLabel = submitted
          ? new Date(submitted).toLocaleDateString(undefined, {
              month: "short",
              day: "numeric",
              year: "numeric",
            })
          : "—";

        return {
          id: s.id,
          title: s.eventTitle,
          subtitle: s.category ?? "",
          institution: institutionName,
          submitted: submittedLabel,
          rawStatus: s.status,
          status: statusDisplay(s.status),
        };
      });
  }, [submissions, institutions, user]);

  const statusCounts = useMemo(() => {
    const counts = {
      all: allActivities.length,
      published: 0,
      scheduled: 0,
      review: 0,
      revision: 0,
      draft: 0,
      failed: 0,
    };
    allActivities.forEach((item) => {
      if (
        item.rawStatus === "published" ||
        item.rawStatus === "published_manual" ||
        item.rawStatus === "admin_direct_post"
      ) {
        counts.published++;
      } else if (item.rawStatus === "scheduled") {
        counts.scheduled++;
      } else if (item.rawStatus === "pending" || item.rawStatus === "in_review") {
        counts.review++;
      } else if (item.rawStatus === "needs_revision") {
        counts.revision++;
      } else if (item.rawStatus === "draft") {
        counts.draft++;
      } else if (item.rawStatus === "publish_failed" || item.rawStatus === "rejected") {
        counts.failed++;
      }
    });
    return counts;
  }, [allActivities]);

  const statusTabs = useMemo(() => {
    const tabs = [
      { id: "all", label: "All", count: statusCounts.all },
      { id: "published", label: "Published", count: statusCounts.published },
      { id: "scheduled", label: "Scheduled", count: statusCounts.scheduled },
      { id: "review", label: "Under Review", count: statusCounts.review },
      { id: "revision", label: "Needs Revision", count: statusCounts.revision },
      { id: "draft", label: "Draft", count: statusCounts.draft },
      { id: "failed", label: "Failed", count: statusCounts.failed },
    ];
    return tabs.filter(
      (tab) =>
        tab.id === "all" ||
        tab.count > 0 ||
        tab.id === "published" ||
        tab.id === "scheduled" ||
        tab.id === "review",
    );
  }, [statusCounts]);

  const filteredActivities = useMemo(() => {
    return allActivities.filter((item) => {
      const matchesSearch =
        searchQuery.trim() === "" ||
        item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.subtitle.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.institution.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesStatus =
        statusFilter === "all" ||
        (statusFilter === "published" &&
          (item.rawStatus === "published" ||
            item.rawStatus === "published_manual" ||
            item.rawStatus === "admin_direct_post")) ||
        (statusFilter === "review" &&
          (item.rawStatus === "pending" || item.rawStatus === "in_review")) ||
        (statusFilter === "scheduled" && item.rawStatus === "scheduled") ||
        (statusFilter === "failed" &&
          (item.rawStatus === "publish_failed" || item.rawStatus === "rejected")) ||
        (statusFilter === "draft" && item.rawStatus === "draft") ||
        (statusFilter === "revision" && item.rawStatus === "needs_revision");

      return matchesSearch && matchesStatus;
    });
  }, [allActivities, searchQuery, statusFilter]);

  return (
    <div id="screen-recent-activity" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body">
        <button
          type="button"
          className="dash-back-btn"
          onClick={() => navigate("/dashboard")}
        >
          <i className="ti ti-arrow-left"></i> Back to Dashboard
        </button>

        <div className="dash-view-header">
          <h1 className="dash-view-title">Recent Activity</h1>
          <p className="dash-view-desc">
            Complete overview of submissions and event activities across your workspace.
          </p>
        </div>

        <div className="card-wrap" style={{ marginBottom: "16px" }}>
          <div className="dash-card-toolbar">
            <div className="im-status-tabs" role="group" aria-label="Filter activities by status">
              {statusTabs.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  className={`im-status-tab${statusFilter === tab.id ? " is-active" : ""}`}
                  onClick={() => setStatusFilter(tab.id)}
                  aria-pressed={statusFilter === tab.id}
                >
                  {tab.label}
                  <span className="im-status-tab-count">{tab.count}</span>
                </button>
              ))}
            </div>

            <div className="im-search-wrap">
              <i className="ti ti-search im-search-icon" aria-hidden="true"></i>
              <input
                className="im-search-input"
                type="search"
                placeholder="Search activities..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Search activities"
              />
            </div>
          </div>
        </div>

        <div className="card-wrap">
          <table className="data-table" id="activity-full-table">
            <thead>
              <tr>
                <th>Event / Post</th>
                <th>Institution</th>
                <th>Submitted</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody
              id="activity-full-body"
              key={`${statusFilter}-${searchQuery}`}
              className="act-table-animate"
            >
              {loading ? (
                Array.from({ length: 6 }).map((_, index) => (
                  <tr key={`skel-act-${index}`} className="dash-skeleton-row">
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-title"></div>
                      <br />
                      <div className="dash-skeleton dash-skeleton-line is-sub"></div>
                    </td>
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-inst"></div>
                    </td>
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-date"></div>
                    </td>
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-pill"></div>
                    </td>
                  </tr>
                ))
              ) : filteredActivities.length === 0 ? (
                <tr>
                  <td
                    colSpan={4}
                    style={{
                      textAlign: "center",
                      padding: "44px 20px",
                      color: "var(--d-muted)",
                      fontSize: 13,
                    }}
                  >
                    <div
                      style={{
                        width: 46,
                        height: 46,
                        borderRadius: "50%",
                        background: "#eff6ff",
                        border: "1px solid #dbeafe",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        margin: "0 auto 12px",
                      }}
                    >
                      <i
                        className="ti ti-photo-off"
                        style={{
                          fontSize: 24,
                          color: "#3b82f6",
                        }}
                      ></i>
                    </div>
                    <div
                      style={{
                        fontWeight: 600,
                        color: "#1e293b",
                        marginBottom: 4,
                      }}
                    >
                      {searchQuery || statusFilter !== "all"
                        ? "No matching activities found"
                        : "No recent activities yet"}
                    </div>
                    <div style={{ color: "#64748b", fontSize: 12.5 }}>
                      {searchQuery || statusFilter !== "all"
                        ? "Try adjusting your search query or status filter."
                        : "Submissions will appear here once created."}
                    </div>
                  </td>
                </tr>
              ) : (
                filteredActivities.map((row) => (
                  <tr key={`${row.id}-${row.title}-${row.submitted}`}>
                    <td>
                      <div className="act-title">{row.title}</div>
                      {row.subtitle && (
                        <span className="act-category">{row.subtitle}</span>
                      )}
                    </td>
                    <td className="act-institution">{row.institution}</td>
                    <td className="act-date">{row.submitted}</td>
                    <td>
                      <span className={`status-pill ${row.status.className}`}>
                        <i
                          className={row.status.icon}
                          style={{ fontSize: 13.5 }}
                        ></i>{" "}
                        {row.status.label}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function statusDisplay(status: SubmissionSummary["status"]): ActivityItem["status"] {
  switch (status) {
    case "draft":
      return { label: "Draft", icon: "ti ti-pencil", className: "pill-draft" };
    case "pending":
      return { label: "Pending Review", icon: "ti ti-clock", className: "pill-pending" };
    case "in_review":
      return { label: "In Review", icon: "ti ti-eye", className: "pill-review" };
    case "needs_revision":
      return { label: "Needs Revision", icon: "ti ti-pencil-minus", className: "pill-revision" };
    case "scheduled":
      return { label: "Scheduled", icon: "ti ti-calendar-event", className: "pill-scheduled" };
    case "publish_failed":
      return { label: "Publish Failed", icon: "ti ti-alert-circle", className: "pill-failed" };
    case "published":
    case "published_manual":
    case "admin_direct_post":
      return { label: "Published", icon: "ti ti-circle-check", className: "pill-published" };
    case "rejected":
      return { label: "Rejected", icon: "ti ti-x", className: "pill-rejected" };
    default:
      return { label: status, icon: "ti ti-circle", className: "" };
  }
}

function getInstitutionName(user: User | null): string {
  if (!user) return "Institution";
  if (user.role === "admin" || user.role === "moderator") return "DASIG Network";
  const explicit = user.inst?.trim();
  if (explicit && explicit !== user.institutionId) return explicit;
  const emailDomain =
    user.email.split("@")[1]?.split(".")[0]?.toLowerCase() || "";
  return emailDomain.toUpperCase() || "Institution";
}
