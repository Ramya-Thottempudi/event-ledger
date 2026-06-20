package com.eventledger.gateway;

import com.eventledger.shared.EventType;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TracePropagationIT {

    private static MockWebServer mockAccountService;

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeAll
    static void setUp() throws Exception {
        mockAccountService = new MockWebServer();
        mockAccountService.start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        mockAccountService.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.url", () ->
                "http://localhost:" + mockAccountService.getPort());
    }

    @Test
    void shouldPropagateTraceIdHeaderToAccountService() throws Exception {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        // Arrange
        String responseJson = """
                {"transactionId":"evt-trace-001","accountId":"acct-trace-123",
                 "newBalance":200.00,"currency":"USD","status":"SUCCESS",
                 "message":null,"processedAt":"2026-05-15T14:02:11Z"}
                """;
        mockAccountService.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setResponseCode(200));

        TransactionRequest request = new TransactionRequest(
                "evt-trace-001", "acct-trace-123", EventType.CREDIT,
                BigDecimal.valueOf(200.00), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);

        // Act
        restClient.post()
                .uri("/events")
                .body(request)
                .retrieve()
                .toEntity(TransactionResponse.class);

        // Assert: Account Service request carries trace-id header
        RecordedRequest accountRequest = mockAccountService.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(accountRequest);

        String traceId = accountRequest.getHeader("trace-id");
        assertNotNull(traceId, "trace-id header must be propagated to Account Service");
        assertFalse(traceId.isBlank(), "trace-id must not be empty");

        // Verify the trace-id is a valid UUID format
        assertDoesNotThrow(() -> java.util.UUID.fromString(traceId),
                "trace-id must be a valid UUID");
    }
}
