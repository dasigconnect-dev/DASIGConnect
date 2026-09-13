import type { QueryClient, QueryKey } from "@tanstack/react-query";

export async function invalidateQueryRoots(
  queryClient: QueryClient,
  roots: readonly QueryKey[],
): Promise<void> {
  const uniqueRoots = new Map<string, QueryKey>();
  roots.forEach((queryKey) => uniqueRoots.set(JSON.stringify(queryKey), queryKey));

  await Promise.all(
    Array.from(uniqueRoots.values(), (queryKey) =>
      queryClient.invalidateQueries({ queryKey }),
    ),
  );
}
