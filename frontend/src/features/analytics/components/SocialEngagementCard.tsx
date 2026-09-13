import type { FacebookEngagementSummaryDto } from "../../../api/analyticsApi";
import FacebookEngagementPanel from "./FacebookEngagementPanel";

interface Props {
  data: FacebookEngagementSummaryDto;
  onOpenReport: () => void;
  isAdmin?: boolean;
}

/**
 * Dedicated, top-level Social Engagement section. Kept separate from the
 * System Operations card so audience metrics read as their own story rather
 * than a sub-panel of reliability telemetry.
 */
export default function SocialEngagementCard({ data, onOpenReport, isAdmin = false }: Readonly<Props>) {
  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">
            Social Engagement
          </h3>
          <p className="analytics-chart-subtitle">
            {isAdmin ? "Reach, reactions, comments and shares" : "Reactions, comments and shares"} on posts
            published to the DASIG Facebook Page
          </p>
        </div>
      </div>
      <FacebookEngagementPanel data={data} onOpenReport={onOpenReport} isAdmin={isAdmin} />
    </div>
  );
}
