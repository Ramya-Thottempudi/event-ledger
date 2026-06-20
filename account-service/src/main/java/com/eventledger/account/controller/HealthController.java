package com.eventledger.account.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;

@RestController
public class HealthController {
    private final DataSource dataSource;
    private final Instant startTime;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
        this.startTime = Instant.now();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        String dbStatus = "UP";
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                dbStatus = "DOWN";
            }
        } catch (Exception e) {
            dbStatus = "DOWN: " + e.getMessage();
        }

        boolean isHealthy = dbStatus.equals("UP");
        Map<String, Object> status = Map.of(
            "service", "account-service",
            "status", isHealthy ? "UP" : "DOWN",
            "database", dbStatus,
            "uptime", Duration.between(startTime, Instant.now()).toSeconds() + "s",
            "timestamp", Instant.now().toString()
        );

        return isHealthy ? ResponseEntity.ok(status)
                         : ResponseEntity.status(503).body(status);
    }
}
