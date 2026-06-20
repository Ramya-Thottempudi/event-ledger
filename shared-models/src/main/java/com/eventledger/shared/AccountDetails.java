package com.eventledger.shared;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountDetails(
    String accountId,
    BigDecimal balance,
    String currency,
    Instant lastUpdated,
    int transactionCount
) {}
