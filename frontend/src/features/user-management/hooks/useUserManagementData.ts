import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  listAdmins,
  listInstitutions,
  listNetworkUsers,
  listPendingNetworkInvitations,
  type PendingInvitationResponse,
  type UserProfileResponse,
} from "../../../api/authApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";
import type { InstitutionOption } from "../types";
import { toInstitutionOption } from "../types";

interface UserManagementData {
  institutions: InstitutionOption[];
  managedUsers: UserProfileResponse[];
  pendingInvitations: PendingInvitationResponse[];
  isOwner: boolean;
  adminSlotsOpen: number;
}

export interface UserManagementErrors {
  institutions: string;
  management: string;
}

const USER_MANAGEMENT_STALE_TIME_MS = 60_000;
const ADMIN_LIMIT = 3;

export const emptyUserManagementData: UserManagementData = {
  institutions: [],
  managedUsers: [],
  pendingInvitations: [],
  isOwner: false,
  adminSlotsOpen: 0,
};

export function useUserManagementData(user: User) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return useQuery({
    queryKey: queryKeys.users.all({
      role: user.role,
      userId: userScope,
      institutionId: null,
      scope: "network",
    }),
    queryFn: ({ signal }) => fetchUserManagementData(user, signal),
    enabled: user.role === "admin",
    staleTime: USER_MANAGEMENT_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function useInvalidateUserManagementData() {
  const queryClient = useQueryClient();

  return async function invalidateUserManagementData() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["users"] }),
      queryClient.invalidateQueries({ queryKey: ["administrators"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      queryClient.invalidateQueries({ queryKey: ["analytics"] }),
    ]);
  };
}

async function fetchUserManagementData(user: User, signal?: AbortSignal): Promise<UserManagementData & { errors: UserManagementErrors }> {
  const [institutionsResult, usersResult, pendingResult, adminsResult] = await Promise.allSettled([
    listInstitutions(signal),
    listNetworkUsers(signal),
    listPendingNetworkInvitations(signal),
    listAdmins(signal),
  ]);

  if (signal?.aborted) {
    throw new DOMException("User management query cancelled.", "AbortError");
  }

  const institutions =
    institutionsResult.status === "fulfilled"
      ? institutionsResult.value.data.map(toInstitutionOption)
      : [];
  const managedUsers = usersResult.status === "fulfilled" ? usersResult.value.data : [];
  const pendingInvitations = pendingResult.status === "fulfilled" ? pendingResult.value.data : [];
  const admins = adminsResult.status === "fulfilled" ? adminsResult.value.data : [];

  const me = admins.find((admin) => admin.email.toLowerCase() === user.email.toLowerCase());
  const activeAdmins = admins.filter((admin) => admin.accountState.toLowerCase() === "active").length;

  return {
    institutions,
    managedUsers,
    pendingInvitations,
    isOwner: me?.adminOwner === true,
    adminSlotsOpen: Math.max(0, ADMIN_LIMIT - activeAdmins),
    errors: {
      institutions:
        institutionsResult.status === "rejected"
          ? getApiErrorMessage(institutionsResult.reason, "Unable to load institutions.")
          : "",
      management: [
        usersResult.status === "rejected"
          ? `Users: ${getApiErrorMessage(usersResult.reason, "Unable to load users.")}`
          : null,
        pendingResult.status === "rejected"
          ? `Pending invitations: ${getApiErrorMessage(pendingResult.reason, "Unable to load invitations.")}`
          : null,
        adminsResult.status === "rejected"
          ? `Admin accounts: ${getApiErrorMessage(adminsResult.reason, "Unable to load admin slots.")}`
          : null,
      ].filter(Boolean).join(" "),
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
