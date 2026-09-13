import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  listAdmins,
  listInstitutions,
  listPendingAdminInvitations,
  type PendingInvitationResponse,
  type UserProfileResponse,
} from "../../../api/authApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";
import type { InstitutionOption } from "../../user-management/types";
import { toInstitutionOption } from "../../user-management/types";

export interface AdministratorManagementData {
  admins: UserProfileResponse[];
  pendingInvitations: PendingInvitationResponse[];
  institutions: InstitutionOption[];
  errors: AdministratorManagementErrors;
}

export interface AdministratorManagementErrors {
  roster: string;
  institutions: string;
}

const ADMINISTRATOR_MANAGEMENT_STALE_TIME_MS = 60_000;

export const emptyAdministratorManagementData: AdministratorManagementData = {
  admins: [],
  pendingInvitations: [],
  institutions: [],
  errors: {
    roster: "",
    institutions: "",
  },
};

export function useAdministratorManagementData(user: User) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return useQuery({
    queryKey: queryKeys.administrators.all({
      role: user.role,
      userId: userScope,
      scope: "network",
    }),
    queryFn: ({ signal }) => fetchAdministratorManagementData(signal),
    enabled: user.role === "admin",
    staleTime: ADMINISTRATOR_MANAGEMENT_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function useInvalidateAdministratorManagementData() {
  const queryClient = useQueryClient();

  return async function invalidateAdministratorManagementData() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["administrators"] }),
      queryClient.invalidateQueries({ queryKey: ["users"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      queryClient.invalidateQueries({ queryKey: ["analytics"] }),
    ]);
  };
}

async function fetchAdministratorManagementData(signal?: AbortSignal): Promise<AdministratorManagementData> {
  const [adminsResult, pendingResult, institutionsResult] = await Promise.allSettled([
    listAdmins(signal),
    listPendingAdminInvitations(signal),
    listInstitutions(signal),
  ]);

  if (signal?.aborted) {
    throw new DOMException("Administrator management query cancelled.", "AbortError");
  }

  return {
    admins: adminsResult.status === "fulfilled" ? adminsResult.value.data : [],
    pendingInvitations: pendingResult.status === "fulfilled" ? pendingResult.value.data : [],
    institutions:
      institutionsResult.status === "fulfilled"
        ? institutionsResult.value.data.map(toInstitutionOption)
        : [],
    errors: {
      roster: [
        adminsResult.status === "rejected"
          ? `Admin accounts: ${getApiErrorMessage(adminsResult.reason, "Unable to load accounts.")}`
          : null,
        pendingResult.status === "rejected"
          ? `Pending invitations: ${getApiErrorMessage(pendingResult.reason, "Unable to load invitations.")}`
          : null,
      ].filter(Boolean).join(" "),
      institutions:
        institutionsResult.status === "rejected"
          ? getApiErrorMessage(institutionsResult.reason, "Unable to load institutions.")
          : "",
    },
  };
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) return fallback;
  const response = error.response;
  if (isRecord(response)) {
    const data = response.data;
    if (isRecord(data)) {
      if (typeof data.error === "string") return data.error;
      if (typeof data.message === "string") return data.message;
    }
  }
  return typeof error.message === "string" ? error.message : fallback;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
