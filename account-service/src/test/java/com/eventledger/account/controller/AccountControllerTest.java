package com.eventledger.account.controller;

import com.eventledger.account.service.AccountService;
import com.eventledger.shared.AccountDetails;
import com.eventledger.shared.EventType;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AccountController controller = new AccountController(accountService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void shouldApplyTransaction() throws Exception {
        TransactionRequest request = new TransactionRequest(
            "evt-001", "acct-123", EventType.CREDIT,
            BigDecimal.valueOf(150.00), "USD", Instant.now(), null);

        when(accountService.applyTransaction(any()))
            .thenReturn(TransactionResponse.success("evt-001", "acct-123", BigDecimal.valueOf(150.00), "USD"));

        mockMvc.perform(post("/accounts/{accountId}/transactions", "acct-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.newBalance").value(150.00));
    }

    @Test
    void shouldReturn400ForMismatchedAccountId() throws Exception {
        TransactionRequest request = new TransactionRequest(
            "evt-002", "acct-456", EventType.DEBIT,
            BigDecimal.valueOf(50.00), "USD", Instant.now(), null);

        mockMvc.perform(post("/accounts/{accountId}/transactions", "acct-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn200ForGetBalance() throws Exception {
        AccountDetails details =
            new AccountDetails("acct-123", BigDecimal.valueOf(500), "USD", null, 5);
        when(accountService.getAccountDetails("acct-123")).thenReturn(details);

        mockMvc.perform(get("/accounts/{accountId}/balance", "acct-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(500));
    }

    @Test
    void shouldReturn404ForUnknownAccount() throws Exception {
        when(accountService.getAccountDetails("unknown"))
            .thenThrow(new IllegalArgumentException("Account not found: unknown"));

        mockMvc.perform(get("/accounts/{accountId}/balance", "unknown"))
            .andExpect(status().isNotFound());
    }
}
