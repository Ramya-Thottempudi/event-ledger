package com.eventledger.shared;

import java.time.Instant;
import java.util.List;

public record ValidationError(
    String error,
    List<String> details,
    String traceId,
    Instant timestamp
) {
    public static ValidationError of(String error, List<String> details) {
        return new ValidationError(error, details, null, Instant.now());
    }
}
