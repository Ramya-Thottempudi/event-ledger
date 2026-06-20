package com.eventledger.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceClientTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .build();
        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
    }

    @Test
    void circuitBreakerShouldBeInitiallyClosed() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void circuitBreakerShouldOpenAfterFailures() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("accountService");

        // Simulate failures
        for (int i = 0; i < 5; i++) {
            try {
                cb.acquirePermission();
                cb.onError(100, TimeUnit.MILLISECONDS,
                    new RuntimeException("Service unavailable"));
            } catch (Exception ignored) {}
        }

        // Circuit should be open after exceeding threshold
        assertTrue(cb.getState() == CircuitBreaker.State.OPEN ||
                   cb.getState() == CircuitBreaker.State.HALF_OPEN);
    }
}
