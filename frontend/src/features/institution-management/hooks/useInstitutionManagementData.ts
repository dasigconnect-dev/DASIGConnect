import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getInstitutionLogoUrl,
  getPendingInvitationCount,
  getUserCounts,
  listInstitutions,
  listPendingInvitations,
  listUsers,
  type PendingInvitationResponse,
  type UserProfileResponse,
} from "../../../api/authApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";

export interface InstitutionWithStats {
  id: string;
  name: string;
  code: string;
  emailDomain: string;
  status: string;
  logoUrl: string | null;
  contributors: number;
  moderators: number;
  pendingInvitations: number;
  statsLoading: boolean;
  isProtected?: boolean;
}

export interface InstitutionDetailData {
  managedUsers: UserProfileResponse[];
  pendingInvitations: PendingInvitationResponse[];
}

const INSTITUTION_REGISTRY_STALE_TIME_MS = 5 * 60_000;
const INSTITUTION_DETAIL_STALE_TIME_MS = 60_000;

export const emptyInstitutionDetailData: InstitutionDetailData = {
  managedUsers: [],
  pendingInvitations: [],
};

export function useInstitutionRegistryData(user: User) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return useQuery({
    queryKey: queryKeys.institutions.all({
      role: user.role,
      userId: userScope,
    }),
    queryFn: ({ signal }) => fetchInstitutionRegistry(signal),
    enabled: user.role === "admin" || user.role === "moderator",
    staleTime: INSTITUTION_REGISTRY_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function useInstitutionDetailData(user: User, institutionId: string | null) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return useQuery({
    queryKey: queryKeys.users.all({
      role: user.role,
      userId: userScope,
      institutionId,
      scope: "institution",
    }),
    queryFn: ({ signal }) => fetchInstitutionDetail(institutionId, signal),
    enabled: Boolean(institutionId) && (user.role === "admin" || user.role === "moderator"),
    staleTime: INSTITUTION_DETAIL_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function useInvalidateInstitutionManagementData() {
  const queryClient = useQueryClient();

  return async function invalidateInstitutionManagementData() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["institutions"] }),
      queryClient.invalidateQueries({ queryKey: ["users"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      queryClient.invalidateQueries({ queryKey: ["analytics"] }),
    ]);
  };
}

async function fetchInstitutionRegistry(signal?: AbortSignal): Promise<InstitutionWithStats[]> {
  const response = await listInstitutions(signal);
  const base = response.data.map((item): InstitutionWithStats => ({
    id: item.id,
    name: item.name,
    code: item.institutionCode,
    emailDomain: item.emailDomain,
    status: item.status,
    logoUrl: item.hasLogo ? getInstitutionLogoUrl(item.id, item.logoUpdatedAt) : null,
    contributors: 0,
    moderators: 0,
    pendingInvitations: 0,
    statsLoading: true,
    isProtected: item.isProtected ?? item.protected,
  }));

  const withStats = await Promise.all(
    base.map(async (institution) => {
      try {
        const [countsRes, pendingRes] = await Promise.all([
          getUserCounts(institution.id, signal),
          getPendingInvitationCount(institution.id, signal),
        ]);
        return {
          ...institution,
          contributors: countsRes.data.contributors,
          moderators: countsRes.data.moderators,
          pendingInvitations: pendingRes.data.pendingInvitations,
          statsLoading: false,
        };
      } catch (error) {
        if (isCanceledError(error, signal)) throw error;
        return { ...institution, statsLoading: false };
      }
    }),
  );

  if (signal?.aborted) {
    throw new DOMException("Institution registry query cancelled.", "AbortError");
  }

  return withStats;
}

async function fetchInstitutionDetail(
  institutionId: string | null,
  signal?: AbortSignal,
): Promise<InstitutionDetailData> {
  if (!institutionId) return emptyInstitutionDetailData;

  const [usersResponse, pendingResponse] = await Promise.all([
    listUsers(institutionId, signal),
    listPendingInvitations(institutionId, signal),
  ]);

  return {
    managedUsers: usersResponse.data,
    pendingInvitations: pendingResponse.data,
  };
}

function isCanceledError(error: unknown, signal?: AbortSignal) {
  if (signal?.aborted) return true;
  if (typeof error !== "object" || error === null) return false;
  const maybeCanceled = error as { code?: string; name?: string };
  return maybeCanceled.code === "ERR_CANCELED" || maybeCanceled.name === "CanceledError" || maybeCanceled.name === "AbortError";
}
