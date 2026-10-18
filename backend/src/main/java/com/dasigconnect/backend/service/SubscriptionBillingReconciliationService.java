package com.dasigconnect.backend.service;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.dasigconnect.backend.event.SubscriptionPaymentAttentionEvent;
import com.dasigconnect.backend.external.PaymongoClient;
import com.dasigconnect.backend.model.entity.SubscriptionPaymentLink;
import com.dasigconnect.backend.model.entity.SubscriptionPaymentLinkStatus;
import com.dasigconnect.backend.repository.SubscriptionPaymentLinkRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionBillingReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingReconciliationService.class);
    private final PaymongoProperties properties;
    private final PaymongoClient paymongoClient;
    private final SubscriptionPaymentLinkRepository linkRepository;
    private final SubscriptionPaymentSettlementService settlementService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionBillingReconciliationService(
            PaymongoProperties properties,
            PaymongoClient paymongoClient,
            SubscriptionPaymentLinkRepository linkRepository,
            SubscriptionPaymentSettlementService settlementService,
            ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.paymongoClient = paymongoClient;
        this.linkRepository = linkRepository;
        this.settlementService = settlementService;
        this.eventPublisher = eventPublisher;
    }

    public ReconciliationResult reconcile() {
        if (!properties.enabled()) return new ReconciliationResult(0, 0, 0);
        int checked = 0;
        int settled = 0;
        int failed = 0;
        for (SubscriptionPaymentLink link : linkRepository.findByStatus(SubscriptionPaymentLinkStatus.ACTIVE)) {
            checked++;
            try {
                PaymongoClient.PaymentLink provider = paymongoClient.retrievePaymentLink(link.getExternalLinkId());
                if (provider.liveMode() != link.isLiveMode()
                        || (provider.amountCentavos() > 0
                            && provider.amountCentavos() != link.getInvoice().getAmountCentavos())) {
                    throw new SubscriptionPaymentSettlementService.PaymentValidationException(
                            "Provider link identity or amount did not match the local invoice.");
                }
                for (PaymongoClient.Payment payment : provider.payments()) {
                    if (settlementService.settle(provider.id(), payment, "RECONCILIATION")
                            == SubscriptionPaymentSettlementService.SettlementResult.SETTLED) {
                        settled++;
                    }
                }
                if ("archived".equalsIgnoreCase(provider.status())) markArchived(link.getId());
            } catch (Exception error) {
                failed++;
                log.warn("PayMongo reconciliation failed for link {}: {}", link.getId(), error.getMessage());
            }
        }
        if (failed > 0) {
            eventPublisher.publishEvent(new SubscriptionPaymentAttentionEvent(
                    failed + " PayMongo payment link(s) require manual reconciliation."));
        }
        return new ReconciliationResult(checked, settled, failed);
    }

    @Transactional
    public void markArchived(java.util.UUID linkId) {
        linkRepository.findById(linkId).ifPresent(link -> {
            link.setStatus(SubscriptionPaymentLinkStatus.ARCHIVED);
            link.setArchivedAt(Instant.now());
            linkRepository.save(link);
        });
    }

    public record ReconciliationResult(int checked, int settled, int failed) {}
}
