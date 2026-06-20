package com.eventledger.gateway.controller;

import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.service.EventService;
import com.eventledger.shared.EventType;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private EventService eventService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        EventController controller = new EventController(eventService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldSubmitEvent() throws Exception {
        TransactionRequest request = new TransactionRequest(
            "evt-001", "acct-123", EventType.CREDIT,
            BigDecimal.valueOf(150.00), "USD", Instant.parse("2026-05-15T14:02:11Z"), null);

        when(eventService.isAccountServiceAvailable()).thenReturn(true);
        when(eventService.submitEvent(any(), anyString()))
            .thenReturn(TransactionResponse.success("evt-001", "acct-123", BigDecimal.valueOf(150.00), "USD"));

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.newBalance").value(150.00));
    }

    @Test
    void shouldReturn400ForInvalidEvent() throws Exception {
        String invalidJson = """
            {
                "eventId": "evt-002",
                "accountId": "acct-123",
                "type": "INVALID_TYPE",
                "amount": -100,
                "currency": "USD",
                "eventTimestamp": "2026-05-15T14:02:11Z"
            }
            """;

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnDuplicateEvent() throws Exception {
        TransactionRequest request = new TransactionRequest(
            "evt-001", "acct-123", EventType.CREDIT,
            BigDecimal.valueOf(150.00), "USD", Instant.parse("2026-05-15T14:02:11Z"), null);

        when(eventService.isAccountServiceAvailable()).thenReturn(true);
        when(eventService.submitEvent(any(), anyString()))
            .thenReturn(TransactionResponse.success("evt-001", "acct-123", BigDecimal.valueOf(150.00), "USD"));

        // First call - should produce 201
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Simulate duplicate - eventService.getEvent returns existing event
        EventRecord existing = new EventRecord();
        existing.setEventId("evt-001");
        existing.setStatus("PROCESSED");
        when(eventService.getEvent("evt-001")).thenReturn(existing);

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void shouldReturn503WhenCircuitBreakerOpen() throws Exception {
        TransactionRequest request = new TransactionRequest(
            "evt-003", "acct-123", EventType.CREDIT,
            BigDecimal.valueOf(100.00), "USD", Instant.now(), null);

        when(eventService.isAccountServiceAvailable()).thenReturn(false);

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void shouldReturn200ForEventLookup() throws Exception {
        EventRecord event = new EventRecord();
        event.setEventId("evt-001");
        event.setAccountId("acct-123");
        when(eventService.getEvent("evt-001")).thenReturn(event);

        mockMvc.perform(get("/events/{eventId}", "evt-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt-001"));
    }
}
