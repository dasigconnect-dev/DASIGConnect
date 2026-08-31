/**
 * Registry for in-memory caches that live at module scope.
 *
 * `handleLogout` navigates client-side (no page reload), so a plain
 * `let cached = …` at module scope survives a logout and the next user on the
 * same tab can briefly see the previous user's role-scoped data before the
 * background refetch corrects it. Each cache module registers a reset here and
 * `handleLogout` calls {@link clearAppCaches} on sign-out.
 */
const resets = new Set<() => void>();

/** Register a cache-clearing callback. Call once at module scope. */
export function registerAppCacheReset(reset: () => void): void {
  resets.add(reset);
}

/** Clear every registered in-memory cache. Called on logout. */
export function clearAppCaches(): void {
  for (const reset of resets) {
    try {
      reset();
    } catch {
      // A misbehaving reset must not block the rest.
    }
  }
}
