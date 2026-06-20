package com.eventledger.account.service;

import com.eventledger.account.entity.Account;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import com.eventledger.shared.EventType;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private AccountService accountService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        accountService = new AccountService(accountRepository, transactionRepository, objectMapper);
    }

    @Test
    void shouldApplyCreditTransaction() {
        String accountId = "acct-123";
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account(accountId, "USD")));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.existsByEventId(any())).thenReturn(false);

        TransactionRequest request = new TransactionRequest(
            "evt-001", accountId, EventType.CREDIT,
            BigDecimal.valueOf(150.00), "USD", Instant.now(), null);

        TransactionResponse response = accountService.applyTransaction(request);

        assertEquals("SUCCESS", response.status());
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(response.newBalance()));
        verify(accountRepository, times(1)).save(any());
    }

    @Test
    void shouldApplyDebitTransaction() {
        String accountId = "acct-123";
        Account account = new Account(accountId, "USD");
        account.setBalance(BigDecimal.valueOf(200.00));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.existsByEventId(any())).thenReturn(false);

        TransactionRequest request = new TransactionRequest(
            "evt-002", accountId, EventType.DEBIT,
            BigDecimal.valueOf(50.00), "USD", Instant.now(), null);

        TransactionResponse response = accountService.applyTransaction(request);

        assertEquals("SUCCESS", response.status());
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(response.newBalance()));
    }

    @Test
    void shouldRejectDuplicateEvent() {
        String accountId = "acct-123";
        when(transactionRepository.existsByEventId("evt-001")).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account(accountId, "USD")));

        TransactionRequest request = new TransactionRequest(
            "evt-001", accountId, EventType.CREDIT,
            BigDecimal.valueOf(100.00), "USD", Instant.now(), null);

        TransactionResponse response = accountService.applyTransaction(request);

        assertNotNull(response);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldCreateAccountOnFirstTransaction() {
        String accountId = "new-acct";
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.existsByEventId(any())).thenReturn(false);

        TransactionRequest request = new TransactionRequest(
            "evt-003", accountId, EventType.CREDIT,
            BigDecimal.valueOf(100.00), "USD", Instant.now(), null);

        TransactionResponse response = accountService.applyTransaction(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        verify(accountRepository, atLeastOnce()).save(any());
    }

    @Test
    void shouldHandleOutOfOrderEvents() {
        String accountId = "acct-456";

        // First transaction (later timestamp)
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account(accountId, "USD")));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.existsByEventId("evt-late")).thenReturn(false);

        TransactionRequest lateRequest = new TransactionRequest(
            "evt-late", accountId, EventType.CREDIT,
            BigDecimal.valueOf(200.00), "USD",
            Instant.parse("2026-05-15T15:00:00Z"), null);

        TransactionResponse lateResponse = accountService.applyTransaction(lateRequest);
        assertEquals(0, BigDecimal.valueOf(200.00).compareTo(lateResponse.newBalance()));

        // Second transaction (earlier timestamp) - arrives later
        when(transactionRepository.existsByEventId("evt-early")).thenReturn(false);

        TransactionRequest earlyRequest = new TransactionRequest(
            "evt-early", accountId, EventType.DEBIT,
            BigDecimal.valueOf(50.00), "USD",
            Instant.parse("2026-05-15T14:00:00Z"), null);

        TransactionResponse earlyResponse = accountService.applyTransaction(earlyRequest);

        // Balance should be 200 + (-50) = 150 regardless of arrival order
        assertEquals(0, BigDecimal.valueOf(150.00).compareTo(earlyResponse.newBalance()));
    }
}
