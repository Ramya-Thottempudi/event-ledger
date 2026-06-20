package com.eventledger.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record TransactionRequest(
    @NotBlank String eventId,
    @NotBlank String accountId,
    @NotNull EventType type,
    @Positive @NotNull BigDecimal amount,
    @NotBlank String currency,
    @NotNull Instant eventTimestamp,
    Map<String, String> metadata
) {}
