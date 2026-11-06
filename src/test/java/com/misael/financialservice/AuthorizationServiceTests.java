package com.misael.financialservice;

import java.math.BigDecimal;

import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserType;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.services.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuthorizationServiceTests {

    private static final String AUTHORIZATION_URL = "https://authorization.example/check";

    @Test
    void acceptsOnlyAnExplicitAuthorizationResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(AUTHORIZATION_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"message\":\"Autorizado\"}", MediaType.APPLICATION_JSON));
        AuthorizationService service = new AuthorizationService(builder.build(), AUTHORIZATION_URL);

        service.authorize(new User("Sender", "sender@example.com", "52998224725", "hash", UserType.COMMON),
                new BigDecimal("10.00"));

        server.verify();
    }

    @Test
    void rejectsDeclinedAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(AUTHORIZATION_URL))
                .andRespond(withSuccess("{\"message\":\"Negado\"}", MediaType.APPLICATION_JSON));
        AuthorizationService service = new AuthorizationService(builder.build(), AUTHORIZATION_URL);

        assertThatThrownBy(() -> service.authorize(
                new User("Sender", "sender@example.com", "52998224725", "hash", UserType.COMMON),
                new BigDecimal("10.00")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(403);
    }

    @Test
    void failsClosedWhenAuthorizationServiceIsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(AUTHORIZATION_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        AuthorizationService service = new AuthorizationService(builder.build(), AUTHORIZATION_URL);

        assertThatThrownBy(() -> service.authorize(
                new User("Sender", "sender@example.com", "52998224725", "hash", UserType.COMMON),
                new BigDecimal("10.00")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(503);
        server.verify();
    }
}
