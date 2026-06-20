package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.repository.EventRepository;
import com.eventledger.shared.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;

    public EventService(EventRepository eventRepository,
                        AccountServiceClient accountServiceClient,
                        ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse submitEvent(TransactionRequest request, String traceId) {
        // Idempotency check
        Optional<EventRecord> existing = eventRepository.findById(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate event received: {} (original status: {})", request.eventId(), existing.get().getStatus());
            if ("PROCESSED".equals(existing.get().getStatus())) {
                return TransactionResponse.success(
                    request.eventId(), request.accountId(), BigDecimal.ZERO, request.currency());
            }
            return TransactionResponse.success(
                request.eventId(), request.accountId(), BigDecimal.ZERO, request.currency());
        }

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

        // Forward to Account Service
        try {
            TransactionResponse accountResponse = accountServiceClient.applyTransaction(request, traceId);
            event.setStatus("PROCESSED");
            eventRepository.save(event);
            log.info("Event {} processed successfully", request.eventId());
            return accountResponse;
        } catch (Exception e) {
            event.setStatus("FAILED");
            event.setErrorMessage(e.getMessage());
            eventRepository.save(event);
            log.error("Event {} failed to process: {}", request.eventId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account Service unavailable: " + e.getMessage());
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
