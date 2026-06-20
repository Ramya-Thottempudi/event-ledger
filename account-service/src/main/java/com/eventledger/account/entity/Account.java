package com.eventledger.account.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private String accountId;
    private BigDecimal balance;
    private String currency;
    private Instant lastUpdated;
    private int transactionCount;

    public Account() {}

    public Account(String accountId, String currency) {
        this.accountId = accountId;
        this.balance = BigDecimal.ZERO;
        this.currency = currency;
        this.lastUpdated = Instant.now();
        this.transactionCount = 0;
    }

    // Getters and setters
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Instant lastUpdated) { this.lastUpdated = lastUpdated; }
    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
}
