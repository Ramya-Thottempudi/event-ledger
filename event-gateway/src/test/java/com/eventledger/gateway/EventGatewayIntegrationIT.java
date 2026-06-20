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
class EventGatewayIntegrationIT {

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
    void shouldSubmitEventThroughGatewayToAccountService() throws Exception {
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();

        String responseJson = """
                {"transactionId":"evt-int-001","accountId":"acct-int-123",
                 "newBalance":150.00,"currency":"USD","status":"SUCCESS",
                 "message":null,"processedAt":"2026-05-15T14:02:11Z"}
                """;
        mockAccountService.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setResponseCode(200));

        TransactionRequest request = new TransactionRequest(
                "evt-int-001", "acct-int-123", EventType.CREDIT,
                BigDecimal.valueOf(150.00), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), null);

        ResponseEntity<TransactionResponse> response = restClient.post()
                .uri("/events")
                .body(request)
                .retrieve()
                .toEntity(TransactionResponse.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().status());

        RecordedRequest accountRequest = mockAccountService.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(accountRequest);
        assertEquals("/accounts/acct-int-123/transactions", accountRequest.getPath());
    }
}
