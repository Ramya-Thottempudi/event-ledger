package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.shared.EventType;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private AccountServiceClient accountServiceClient;

    private EventService eventService;
    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        eventService = new EventService(eventRepository, accountServiceClient, objectMapper, meterRegistry);
    }

    @Test
    void shouldSubmitEventAndReturnSuccess() {
        when(accountServiceClient.applyTransaction(any(), anyString()))
            .thenReturn(TransactionResponse.success("evt-001", "acct-123", BigDecimal.valueOf(150), "USD"));
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionRequest request = new TransactionRequest(
            "evt-001", "acct-123", EventType.CREDIT,
            BigDecimal.valueOf(150), "USD", Instant.now(), null);

        TransactionResponse response = eventService.submitEvent(request, "trace-123");

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        verify(eventRepository, times(2)).save(any());
    }

    @Test
    void shouldReturnExistingEventViaGetEventSafe() {
        EventRecord existing = new EventRecord();
        existing.setEventId("evt-001");
        existing.setStatus("PROCESSED");
        when(eventRepository.findById("evt-001")).thenReturn(Optional.of(existing));

        EventRecord found = eventService.getEventSafe("evt-001");

        assertNotNull(found);
        assertEquals("evt-001", found.getEventId());
        assertEquals("PROCESSED", found.getStatus());
    }

    @Test
    void shouldReturnNullForNonExistentEventSafe() {
        when(eventRepository.findById("evt-999")).thenReturn(Optional.empty());

        EventRecord found = eventService.getEventSafe("evt-999");

        assertNull(found);
    }

    @Test
    void shouldFailWhenAccountServiceUnavailable() {
        when(accountServiceClient.applyTransaction(any(), anyString()))
            .thenThrow(new RuntimeException("Connection refused"));
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionRequest request = new TransactionRequest(
            "evt-002", "acct-123", EventType.DEBIT,
            BigDecimal.valueOf(50), "USD", Instant.now(), null);

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> eventService.submitEvent(request, "trace-123"));
    }

    @Test
    void shouldReturnEventById() {
        EventRecord event = new EventRecord();
        event.setEventId("evt-001");
        event.setStatus("PROCESSED");
        when(eventRepository.findById("evt-001")).thenReturn(Optional.of(event));

        EventRecord found = eventService.getEvent("evt-001");

        assertNotNull(found);
        assertEquals("evt-001", found.getEventId());
    }

    @Test
    void shouldReturnEventsByAccount() {
        EventRecord event = new EventRecord();
        event.setEventId("evt-001");
        event.setAccountId("acct-123");
        when(eventRepository.findByAccountIdOrderByEventTimestampAsc("acct-123"))
            .thenReturn(List.of(event));

        List<EventRecord> events = eventService.getEventsByAccount("acct-123");

        assertEquals(1, events.size());
        assertEquals("evt-001", events.get(0).getEventId());
    }
}
