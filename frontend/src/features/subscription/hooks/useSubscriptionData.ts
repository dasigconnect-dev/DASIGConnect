import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  changeSubscriptionPlan,
  getCurrentSubscription,
  getInstitutionSubscriptionUsage,
  getSubscriptionPlans,
  getSubscriptionReconciliation,
  getSubscriptionUsage,
} from "../../../api/subscriptionApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys, queryRoots } from "../../../lib/queryKeys";

const SUBSCRIPTION_STALE_TIME_MS = 60_000;

export function useSubscriptionData(userId?: string | null) {
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
  const plansQuery = useQuery({
    queryKey: queryKeys.subscription.plans(scope),
    queryFn: ({ signal }) => getSubscriptionPlans(signal).then((response) => response.data),
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

  const changePlanMutation = useMutation({
    mutationFn: changeSubscriptionPlan,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryRoots.subscription });
    },
  });

  return {
    statusQuery,
    plansQuery,
    usageQuery,
    institutionsQuery,
    reconciliationQuery,
    changePlanMutation,
  };
}
