package com.eventledger.account.service;

import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.TransactionEntity;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.shared.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Service
public class AccountService {
    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public AccountService(AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse applyTransaction(TransactionRequest request) {
        // Idempotency check - if event already processed, return existing result
        if (transactionRepository.existsByEventId(request.eventId())) {
            log.info("Duplicate event detected: {}", request.eventId());
            Account account = accountRepository.findById(request.accountId()).orElse(null);
            if (account != null) {
                return TransactionResponse.success(
                    request.eventId(), request.accountId(), account.getBalance(), account.getCurrency());
            }
            return TransactionResponse.error(request.accountId(), "Account not found");
        }

        // Get or create account
        Account account = accountRepository.findById(request.accountId())
            .orElseGet(() -> {
                Account newAccount = new Account(request.accountId(), request.currency());
                log.info("Created new account: {}", request.accountId());
                return accountRepository.save(newAccount);
            });

        // Calculate new balance
        BigDecimal amount = request.amount();
        BigDecimal newBalance = switch (request.type()) {
            case CREDIT -> account.getBalance().add(amount);
            case DEBIT -> account.getBalance().subtract(amount);
        };

        // Update account
        account.setBalance(newBalance);
        account.setCurrency(request.currency());
        account.setLastUpdated(Instant.now());
        account.setTransactionCount(account.getTransactionCount() + 1);
        accountRepository.save(account);

        // Record transaction
        TransactionEntity tx = new TransactionEntity();
        tx.setEventId(request.eventId());
        tx.setAccountId(request.accountId());
        tx.setType(request.type());
        tx.setAmount(amount);
        tx.setCurrency(request.currency());
        tx.setEventTimestamp(request.eventTimestamp());
        tx.setProcessedAt(Instant.now());
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            try {
                tx.setMetadata(objectMapper.writeValueAsString(request.metadata()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata for event {}", request.eventId(), e);
            }
        }
        transactionRepository.save(tx);

        log.info("Applied {} {} to account {}: new balance={}",
            request.type(), amount, request.accountId(), newBalance);

        return TransactionResponse.success(
            request.eventId(), request.accountId(), newBalance, account.getCurrency());
    }

    public AccountDetails getAccountDetails(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        return new AccountDetails(
            account.getAccountId(),
            account.getBalance(),
            account.getCurrency(),
            account.getLastUpdated(),
            account.getTransactionCount()
        );
    }

    public BigDecimal getBalance(String accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        return account.getBalance();
    }
}
