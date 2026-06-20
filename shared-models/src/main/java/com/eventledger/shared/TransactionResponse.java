package com.eventledger.shared;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    String transactionId,
    String accountId,
    BigDecimal newBalance,
    String currency,
    String status,
    String message,
    Instant processedAt
) {
    public static TransactionResponse success(String txId, String acctId, BigDecimal balance, String currency) {
        return new TransactionResponse(txId, acctId, balance, currency, "SUCCESS", null, Instant.now());
    }

    public static TransactionResponse error(String acctId, String message) {
        return new TransactionResponse(null, acctId, BigDecimal.ZERO, "USD", "ERROR", message, Instant.now());
    }
}
