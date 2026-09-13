import { QueryClient, type Query } from "@tanstack/react-query";

const DEFAULT_GC_TIME_MS = 5 * 60_000;

function isAuthenticatedQuery(query: Query): boolean {
  return query.meta?.authenticated !== false;
}

export const authenticatedQueryMeta = { authenticated: true } as const;

export const appQueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      gcTime: DEFAULT_GC_TIME_MS,
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 0,
    },
    mutations: {
      retry: 0,
    },
  },
});

export async function clearAuthenticatedQueryCache(): Promise<void> {
  await appQueryClient.cancelQueries({ predicate: isAuthenticatedQuery });
  appQueryClient.removeQueries({ predicate: isAuthenticatedQuery });
  appQueryClient.getMutationCache().clear();
}
