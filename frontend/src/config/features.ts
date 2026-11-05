/**
 * Feature flags for DASIGConnect frontend.
 *
 * Modules under development should default to disabled (`false`)
 * so they do not leak into production environments until ready.
 */

/**
 * Subscription & Billing feature flag.
 * Set VITE_SUBSCRIPTION_ENABLED=true in frontend/.env to enable locally for development.
 */
export const isSubscriptionEnabled = (): boolean => {
  return import.meta.env.VITE_SUBSCRIPTION_ENABLED === "true";
};

