package com.eventledger.gateway.client;

import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
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
    private final Retry retry;
    private final RateLimiter rateLimiter;
    private final String accountServiceBaseUrl;

    public AccountServiceClient(RestClient.Builder restClientBuilder,
                                CircuitBreakerRegistry circuitBreakerRegistry,
                                RetryRegistry retryRegistry,
                                RateLimiterRegistry rateLimiterRegistry,
                                @Value("${account-service.url}") String accountServiceUrl) {
        this.accountServiceBaseUrl = accountServiceUrl;
        this.restClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");
        this.retry = retryRegistry.retry("accountService");
        this.rateLimiter = rateLimiterRegistry.rateLimiter("accountService");
    }

    public TransactionResponse applyTransaction(TransactionRequest request, String traceId) {
        Supplier<TransactionResponse> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker,
            Retry.decorateSupplier(retry,
                RateLimiter.decorateSupplier(rateLimiter, () -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("trace-id", traceId);

                    return restClient.post()
                        .uri("/accounts/{accountId}/transactions", request.accountId())
                        .headers(h -> h.set("trace-id", traceId))
                        .body(request)
                        .retrieve()
                        .toEntity(TransactionResponse.class)
                        .getBody();
                })));

        try {
            return decoratedSupplier.get();
        } catch (Exception e) {
            log.warn("Account Service call failed for event {}: {}", request.eventId(), e.getMessage());
            throw e;
        }
    }

    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }
}
