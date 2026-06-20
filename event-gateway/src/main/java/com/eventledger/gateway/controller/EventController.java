package com.eventledger.gateway.controller;

import com.eventledger.gateway.entity.EventRecord;
import com.eventledger.gateway.service.EventService;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {
    private static final Logger log = LoggerFactory.getLogger(EventController.class);
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<?> submitEvent(@Valid @RequestBody TransactionRequest request) {
        String traceId = MDC.get("trace-id");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            MDC.put("trace-id", traceId);
        }

        // Check if account service is available first (circuit breaker check)
        if (!eventService.isAccountServiceAvailable()) {
            log.warn("Account Service is unavailable (circuit breaker open), rejecting event");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Account Service is temporarily unavailable. Please retry later.",
                    "traceId", traceId,
                    "eventId", request.eventId()
                ));
        }

        try {
            TransactionResponse response = eventService.submitEvent(request, traceId);
            boolean isDuplicate = request.eventId() != null &&
                eventService.getEvent(request.eventId()) != null;

            return isDuplicate
                ? ResponseEntity.status(HttpStatus.OK).body(response)
                : ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to process event {}: {}", request.eventId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Event processing failed. Account Service may be unavailable.",
                    "traceId", traceId,
                    "eventId", request.eventId()
                ));
        }
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable String eventId) {
        try {
            EventRecord event = eventService.getEvent(eventId);
            return ResponseEntity.ok(event);
        } catch (Exception e) {
            log.warn("Event not found: {}", eventId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Event not found: " + eventId));
        }
    }

    @GetMapping
    public ResponseEntity<List<EventRecord>> getEventsByAccount(
            @RequestParam("account") String accountId) {
        List<EventRecord> events = eventService.getEventsByAccount(accountId);
        return ResponseEntity.ok(events);
    }
}
