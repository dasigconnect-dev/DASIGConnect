package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.dto.subscription.SubscriptionPlanDto;
import com.dasigconnect.backend.model.dto.subscription.SubscriptionStatusDto;
import com.dasigconnect.backend.model.entity.OrganizationSubscription;
import com.dasigconnect.backend.repository.OrganizationSubscriptionRepository;
import com.dasigconnect.backend.repository.SubscriptionPlanRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SubscriptionQueryService {

    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final boolean enforcementEnabled;

    public SubscriptionQueryService(
            OrganizationSubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            @Value("${app.subscription.enforcement-enabled:false}") boolean enforcementEnabled) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.enforcementEnabled = enforcementEnabled;
    }

    public SubscriptionStatusDto currentSubscription() {
        OrganizationSubscription subscription = subscriptionRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No organization subscription is configured."));

        return new SubscriptionStatusDto(
                subscription.getOrganization().getId(),
                subscription.getOrganization().getName(),
                subscription.getOrganization().getSlug(),
                subscription.getOrganization().getStatus(),
                subscription.getId(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getGracePeriodEndsAt(),
                SubscriptionPlanDto.from(subscription.getPlan()),
                enforcementEnabled);
    }

    public List<SubscriptionPlanDto> activePlans() {
        return planRepository.findAllByActiveTrueOrderByMonthlyPriceCentavosAscVersionDesc()
                .stream()
                .map(SubscriptionPlanDto::from)
                .toList();
    }
}
