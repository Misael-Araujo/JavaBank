package com.misael.financialservice.services;

import java.util.Map;

import com.misael.financialservice.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    private final RestClient restClient;
    private final String notificationUrl;

    public NotificationService(
            RestClient integrationRestClient,
            @Value("${app.integrations.notification-url}") String notificationUrl) {
        this.restClient = integrationRestClient;
        this.notificationUrl = notificationUrl;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendNotifications(TransferCompletedEvent event) {
        send(event, event.senderEmail(), "Transfer sent");
        send(event, event.recipientEmail(), "Transfer received");
    }

    private void send(TransferCompletedEvent event, String email, String message) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(notificationUrl)
                    .body(new NotificationRequest(email, message))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (response == null || response.values().stream().noneMatch(Boolean.TRUE::equals)) {
                LOGGER.warn("Transfer notification was not acknowledged transactionId={}", event.transactionId());
            }
        } catch (RestClientException exception) {
            LOGGER.warn("Transfer notification failed transactionId={} type={}",
                    event.transactionId(), exception.getClass().getSimpleName());
        }
    }

}
