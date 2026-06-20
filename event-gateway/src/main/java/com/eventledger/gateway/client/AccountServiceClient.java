package com.eventledger.gateway.client;

import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

@Component
public class AccountServiceClient {
    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final String accountServiceBaseUrl;

    public AccountServiceClient(RestClient.Builder restClientBuilder,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                @Value("${account-service.url}") String accountServiceUrl) {
        this.accountServiceBaseUrl = accountServiceUrl;
        this.restClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");
    }

    public TransactionResponse applyTransaction(TransactionRequest request, String traceId) {
        Supplier<TransactionResponse> supplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("trace-id", traceId);

            String url = accountServiceBaseUrl + "/accounts/" + request.accountId() + "/transactions";
            HttpEntity<TransactionRequest> entity = new HttpEntity<>(request, headers);

            return restClient.post()
                .uri("/accounts/{accountId}/transactions", request.accountId())
                .headers(h -> h.set("trace-id", traceId))
                .body(request)
                .retrieve()
                .toEntity(TransactionResponse.class)
                .getBody();
        });

        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Account Service call failed for event {}: {}", request.eventId(), e.getMessage());
            throw e;
        }
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }
}
