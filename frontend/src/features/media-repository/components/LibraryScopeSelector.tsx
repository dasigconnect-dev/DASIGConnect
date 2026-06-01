import { Building2, Network } from "lucide-react";
import BrandedSelect from "../../../components/ui/BrandedSelect";
import type { InstitutionResponse } from "../../../api/authApi";

const NETWORK_SCOPE_VALUE = "__network__";

interface LibraryScopeSelectorProps {
  institutions: InstitutionResponse[];
  selectedInstitutionId: string | null;
  onInstitutionChange: (institutionId: string | null) => void;
  allowNetworkScope?: boolean;
  networkScope?: boolean;
  onNetworkScopeChange?: (enabled: boolean) => void;
  loading?: boolean;
}

export default function LibraryScopeSelector({
  institutions,
  selectedInstitutionId,
  onInstitutionChange,
  allowNetworkScope = false,
  networkScope = false,
  onNetworkScopeChange,
  loading = false,
}: LibraryScopeSelectorProps) {
  const value = allowNetworkScope && networkScope
    ? NETWORK_SCOPE_VALUE
    : selectedInstitutionId ?? "";
  const selectedInstitution = institutions.find((item) => item.id === selectedInstitutionId);

  return (
    <section className="med-scope-bar" aria-label="Media library institution scope">
      <div className="med-scope-label">
        {networkScope ? <Network size={17} aria-hidden="true" /> : <Building2 size={17} aria-hidden="true" />}
        <div>
          <span>Viewing</span>
          <strong>{networkScope ? "All institutions" : selectedInstitution?.name ?? "Select institution"}</strong>
        </div>
      </div>
      <BrandedSelect
        className="med-scope-select"
        value={value}
        loading={loading}
        disabled={loading || institutions.length === 0}
        placeholder="Select institution"
        ariaLabel="Select media library institution"
        hint="Choose an institution before uploading or organizing media."
        options={[
          ...(allowNetworkScope
            ? [{ value: NETWORK_SCOPE_VALUE, label: "Browse all institutions" }]
            : []),
          ...institutions.map((institution) => ({
            value: institution.id,
            label: institution.name,
          })),
        ]}
        onChange={(nextValue) => {
          if (nextValue === NETWORK_SCOPE_VALUE) {
            onNetworkScopeChange?.(true);
            onInstitutionChange(null);
            return;
          }
          onNetworkScopeChange?.(false);
          onInstitutionChange(nextValue || null);
        }}
      />
    </section>
  );
}
