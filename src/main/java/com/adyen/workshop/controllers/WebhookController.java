package com.adyen.workshop.controllers;

import com.adyen.model.notification.NotificationRequest;
import com.adyen.model.notification.NotificationRequestItem;
import com.adyen.util.HMACValidator;
import com.adyen.workshop.Storage;
import com.adyen.workshop.configurations.ApplicationConfiguration;
import org.apache.coyote.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.SignatureException;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for receiving Adyen webhook notifications
 */
@RestController
public class WebhookController {
    private final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ApplicationConfiguration applicationConfiguration;

    private final HMACValidator hmacValidator;

    @Autowired
    public WebhookController(ApplicationConfiguration applicationConfiguration, HMACValidator hmacValidator) {
        this.applicationConfiguration = applicationConfiguration;
        this.hmacValidator = hmacValidator;
    }

    @PostMapping("/webhooks")
    public ResponseEntity<String> webhooks(@RequestBody String json) throws Exception {
        NotificationRequest notificationRequest = NotificationRequest.fromJson(json);
        Optional<NotificationRequestItem> itemOptional =
                notificationRequest.getNotificationItems().stream().findFirst();

        if (itemOptional.isEmpty()) {
            return ResponseEntity.ok("[accepted]");
        }

        NotificationRequestItem item = itemOptional.get();

        boolean valid = hmacValidator.validateHMAC(item, applicationConfiguration.getAdyenHmacKey());
        if (!valid) {
            throw new IllegalArgumentException("Invalid HMAC signature");
        }

        log.info("Received webhook eventCode={}, success={}", item.getEventCode(), item.isSuccess());

        // Workshop-compatible: read token from AUTHORISATION webhook additionalData
        if ("AUTHORISATION".equals(item.getEventCode()) && item.isSuccess()) {
            Map<String, String> additionalData = item.getAdditionalData();
            if (additionalData != null) {
                String token = additionalData.get("recurring.recurringDetailReference");
                if (token == null) {
                    token = additionalData.get("tokenization.storedPaymentMethodId");
                }

                if (token != null && !token.isBlank()) {
                    Storage.STORED_PAYMENT_METHOD_ID = token;
                    log.info("Stored token from webhook: {}", token);
                }
            }
        }

        return ResponseEntity.ok("[accepted]");
    }
}