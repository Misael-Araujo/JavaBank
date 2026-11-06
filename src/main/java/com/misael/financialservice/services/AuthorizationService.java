package com.misael.financialservice.services;

import java.math.BigDecimal;
import java.util.Map;

import com.misael.financialservice.dto.AuthorizationRequest;
import com.misael.financialservice.entities.User;
import com.misael.financialservice.exceptions.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AuthorizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

    private final RestClient restClient;
    private final String authorizationUrl;

    public AuthorizationService(
            RestClient integrationRestClient,
            @Value("${app.integrations.authorization-url}") String authorizationUrl) {
        this.restClient = integrationRestClient;
        this.authorizationUrl = authorizationUrl;
    }

    public void authorize(User payer, BigDecimal amount) {
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(authorizationUrl)
                    .body(new AuthorizationRequest(payer.getId(), amount))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            LOGGER.warn("Transfer authorization service unavailable type={}",
                    exception.getClass().getSimpleName());
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authorization service unavailable",
                    "The transfer could not be authorized at this time.");
        }

        if (response == null || response.isEmpty()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Invalid authorization response",
                    "The transfer could not be authorized at this time.");
        }
        if (response.values().stream().noneMatch(value -> "Autorizado".equals(value))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Transfer not authorized",
                    "The transfer authorization service declined the request.");
        }
    }

}
