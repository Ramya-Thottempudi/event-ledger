package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.shared.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;
    private final Counter eventSubmissionCounter;
    private final Counter eventSuccessCounter;
    private final Counter eventFailureCounter;

    public EventService(EventRepository eventRepository,
                        AccountServiceClient accountServiceClient,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
        this.eventSubmissionCounter = meterRegistry.counter("gateway.events.submitted");
        this.eventSuccessCounter = meterRegistry.counter("gateway.events.success");
        this.eventFailureCounter = meterRegistry.counter("gateway.events.failure");
    }

    public EventRecord getEventSafe(String eventId) {
        return eventRepository.findById(eventId).orElse(null);
    }

    @Transactional
    public TransactionResponse submitEvent(TransactionRequest request, String traceId) {
        // Save event as RECEIVED
        EventRecord event = new EventRecord();
        event.setEventId(request.eventId());
        event.setAccountId(request.accountId());
        event.setType(request.type());
        event.setAmount(request.amount());
        event.setCurrency(request.currency());
        event.setEventTimestamp(request.eventTimestamp());
        event.setReceivedAt(Instant.now());
        event.setStatus("RECEIVED");
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            try {
                event.setMetadata(objectMapper.writeValueAsString(request.metadata()));
            } catch (JacksonException e) {
                log.warn("Failed to serialize metadata", e);
            }
        }
        eventRepository.save(event);
        eventSubmissionCounter.increment();

        // Forward to Account Service
        try {
            TransactionResponse accountResponse = accountServiceClient.applyTransaction(request, traceId);
            event.setStatus("PROCESSED");
            eventRepository.save(event);
            eventSuccessCounter.increment();
            log.info("Event {} processed successfully", request.eventId());
            return accountResponse;
        } catch (Exception e) {
            event.setStatus("FAILED");
            event.setErrorMessage(e.getMessage());
            eventRepository.save(event);
            eventFailureCounter.increment();
            log.error("Event {} failed to process", request.eventId(), e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account Service is temporarily unavailable. Please retry later.");
        }
    }

    public EventRecord getEvent(String eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found: " + eventId));
    }

    public List<EventRecord> getEventsByAccount(String accountId) {
        return eventRepository.findByAccountIdOrderByEventTimestampAsc(accountId);
    }

    public boolean isAccountServiceAvailable() {
        return !accountServiceClient.isCircuitBreakerOpen();
    }
}
