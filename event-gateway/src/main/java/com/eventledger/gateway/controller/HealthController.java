package com.eventledger.gateway.controller;

import com.eventledger.gateway.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    private final DataSource dataSource;
    private final EventService eventService;
    private final Instant startTime;

    public HealthController(DataSource dataSource, EventService eventService) {
        this.dataSource = dataSource;
        this.eventService = eventService;
        this.startTime = Instant.now();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String dbStatus = "UP";
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) dbStatus = "DOWN";
        } catch (Exception e) {
            dbStatus = "DOWN: " + e.getMessage();
        }

        boolean isHealthy = "UP".equals(dbStatus);
        return isHealthy
            ? ResponseEntity.ok(Map.of(
                "service", "event-gateway",
                "status", "UP",
                "database", dbStatus,
                "accountService", eventService.isAccountServiceAvailable() ? "UP" : "CIRCUIT_OPEN",
                "uptime", Duration.between(startTime, Instant.now()).toSeconds() + "s",
                "timestamp", Instant.now().toString()))
            : ResponseEntity.status(503).body(Map.of(
                "service", "event-gateway",
                "status", "DOWN",
                "database", dbStatus));
    }
}
