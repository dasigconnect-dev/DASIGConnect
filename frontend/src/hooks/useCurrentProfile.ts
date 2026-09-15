import { queryOptions, useQuery, type QueryClient } from "@tanstack/react-query";
import { getMe, type UserProfileResponse } from "../api/authApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

const CURRENT_PROFILE_STALE_TIME_MS = 60_000;

export function getCurrentProfileScope(user: User): string {
  return user.id ?? user.email.trim().toLowerCase();
}

export function currentProfileQueryOptions(user: User) {
  const userId = getCurrentProfileScope(user);
  return queryOptions({
    queryKey: queryKeys.principal.profile({ userId }),
    queryFn: ({ signal }) => getMe(signal).then((response) => response.data),
    staleTime: CURRENT_PROFILE_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function useCurrentProfile(user: User) {
  return useQuery(currentProfileQueryOptions(user));
}

export function seedCurrentProfile(queryClient: QueryClient, profile: UserProfileResponse) {
  queryClient.setQueryData(queryKeys.principal.profile({ userId: profile.id }), profile);
}
