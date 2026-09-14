import { queryOptions, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getInstitutionLogoUrl,
  listInstitutionCountSummaries,
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
  isProtected?: boolean;
}

export interface InstitutionDetailData {
  managedUsers: UserProfileResponse[];
  pendingInvitations: PendingInvitationResponse[];
}

const INSTITUTION_REGISTRY_STALE_TIME_MS = 5 * 60_000;
const INSTITUTION_DETAIL_STALE_TIME_MS = 60_000;

export function useInstitutionRegistryData(user: User) {
  return useQuery(institutionRegistryQueryOptions(user));
}

export function useInstitutionCountSummaryData(user: User) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return useQuery({
    queryKey: queryKeys.institutions.summaryCounts({
      role: user.role,
      userId: userScope,
    }),
    queryFn: ({ signal }) => listInstitutionCountSummaries(signal).then((response) => response.data),
    enabled: user.role === "admin" || user.role === "moderator",
    staleTime: INSTITUTION_REGISTRY_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function institutionRegistryQueryOptions(user: User) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return queryOptions({
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
  const enabled = Boolean(institutionId) && (user.role === "admin" || user.role === "moderator");

  const usersQuery = useQuery({
    queryKey: queryKeys.users.all({
      role: user.role,
      userId: userScope,
      institutionId,
      scope: "institution",
    }),
    queryFn: ({ signal }) => listUsers(institutionId!, signal).then((response) => response.data),
    enabled,
    staleTime: INSTITUTION_DETAIL_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const pendingInvitationsQuery = useQuery({
    queryKey: queryKeys.institutions.pendingInvitations({
      role: user.role,
      userId: userScope,
      institutionId: institutionId ?? "none",
    }),
    queryFn: ({ signal }) => listPendingInvitations(institutionId!, signal).then((response) => response.data),
    enabled,
    staleTime: INSTITUTION_DETAIL_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  return {
    data: {
      managedUsers: usersQuery.data ?? [],
      pendingInvitations: pendingInvitationsQuery.data ?? [],
    } satisfies InstitutionDetailData,
    usersQuery,
    pendingInvitationsQuery,
  };
}

export function useInvalidateInstitutionManagementData() {
  const queryClient = useQueryClient();

  return async function invalidateInstitutionManagementData() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["institutions"] }),
      queryClient.invalidateQueries({ queryKey: ["users"] }),
      queryClient.invalidateQueries({ queryKey: ["dashboard"] }),
      queryClient.invalidateQueries({ queryKey: ["analytics"] }),
      queryClient.invalidateQueries({ queryKey: ["settings"] }),
      queryClient.invalidateQueries({ queryKey: ["submissions"] }),
      queryClient.invalidateQueries({ queryKey: ["calendar-events"] }),
      queryClient.invalidateQueries({ queryKey: ["media-assets"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    ]);
  };
}

async function fetchInstitutionRegistry(signal?: AbortSignal): Promise<InstitutionWithStats[]> {
  const response = await listInstitutions(signal);
  return response.data.map((item): InstitutionWithStats => ({
    id: item.id,
    name: item.name,
    code: item.institutionCode,
    emailDomain: item.emailDomain,
    status: item.status,
    logoUrl: item.hasLogo ? getInstitutionLogoUrl(item.id, item.logoUpdatedAt) : null,
    isProtected: item.isProtected ?? item.protected,
  }));
}
