package com.dasigconnect.backend.event;

import com.dasigconnect.backend.service.MessengerDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MessengerNotificationEventListener {
    private final MessengerDeliveryService delivery;
    private final String frontendBaseUrl;

    public MessengerNotificationEventListener(MessengerDeliveryService delivery,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.delivery = delivery;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionPending(SubmissionPendingMessengerEvent event) {
        String message = "New submission awaiting validation: \"" + event.eventTitle()
                + "\". Open DASIGConnect: " + frontendBaseUrl + "/submissions/"
                + event.submissionId();
        event.validatorIds().forEach(userId -> delivery.sendToUser(userId, message));
    }
}
