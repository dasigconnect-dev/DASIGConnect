ALTER TABLE subscription_invoices
    ADD COLUMN subscription_plan_id UUID;

UPDATE subscription_invoices invoice
SET subscription_plan_id = subscription.subscription_plan_id
FROM organization_subscriptions subscription
WHERE subscription.id = invoice.organization_subscription_id
  AND invoice.subscription_plan_id IS NULL;

ALTER TABLE subscription_invoices
    ALTER COLUMN subscription_plan_id SET NOT NULL,
    ADD CONSTRAINT fk_subscription_invoices_plan
        FOREIGN KEY (subscription_plan_id) REFERENCES subscription_plans(id);

CREATE INDEX idx_subscription_invoices_plan
    ON subscription_invoices (subscription_plan_id, created_at DESC);
