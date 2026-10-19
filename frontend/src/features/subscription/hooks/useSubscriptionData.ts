import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createSubscriptionPaymentLink,
  extendSubscriptionGracePeriod,
  getCurrentSubscription,
  getInstitutionSubscriptionUsage,
  getSubscriptionBilling,
  getSubscriptionReconciliation,
  getSubscriptionUsage,
  recordManualSubscriptionRenewal,
  reconcileSubscriptionBilling,
} from "../../../api/subscriptionApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys, queryRoots } from "../../../lib/queryKeys";

const SUBSCRIPTION_STALE_TIME_MS = 60_000;

export function useSubscriptionData(userId?: string | null, isOwner = false) {
  const queryClient = useQueryClient();
  const scope = { userId };
  const common = {
    staleTime: SUBSCRIPTION_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  } as const;

  const statusQuery = useQuery({
    queryKey: queryKeys.subscription.status(scope),
    queryFn: ({ signal }) => getCurrentSubscription(signal).then((response) => response.data),
    ...common,
  });
  const usageQuery = useQuery({
    queryKey: queryKeys.subscription.usage(scope),
    queryFn: ({ signal }) => getSubscriptionUsage(signal).then((response) => response.data),
    ...common,
  });
  const institutionsQuery = useQuery({
    queryKey: queryKeys.subscription.institutions(scope),
    queryFn: ({ signal }) => getInstitutionSubscriptionUsage(signal).then((response) => response.data),
    ...common,
  });
  const reconciliationQuery = useQuery({
    queryKey: queryKeys.subscription.reconciliation(scope),
    queryFn: ({ signal }) => getSubscriptionReconciliation(signal).then((response) => response.data),
    ...common,
  });
  const billingQuery = useQuery({
    queryKey: queryKeys.subscription.billing(scope),
    queryFn: ({ signal }) => getSubscriptionBilling(signal).then((response) => response.data),
    enabled: isOwner,
    ...common,
  });

  const renewalMutation = useMutation({
    mutationFn: () => recordManualSubscriptionRenewal(1),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryRoots.subscription });
    },
  });

  const extendGraceMutation = useMutation({
    mutationFn: () => extendSubscriptionGracePeriod(7),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryRoots.subscription });
    },
  });

  const createPaymentLinkMutation = useMutation({
    mutationFn: (payerEmail?: string) => {
      const planId = statusQuery.data?.plan.id;
      if (!planId) throw new Error("The current subscription plan is unavailable.");
      return createSubscriptionPaymentLink(planId, payerEmail);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryRoots.subscription });
    },
  });

  const reconcileBillingMutation = useMutation({
    mutationFn: reconcileSubscriptionBilling,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryRoots.subscription });
    },
  });

  return {
    statusQuery,
    usageQuery,
    institutionsQuery,
    reconciliationQuery,
    billingQuery,
    renewalMutation,
    extendGraceMutation,
    createPaymentLinkMutation,
    reconcileBillingMutation,
  };
}
