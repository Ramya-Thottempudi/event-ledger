package com.eventledger.account.controller;

import com.eventledger.account.service.AccountService;
import com.eventledger.shared.AccountDetails;
import com.eventledger.shared.TransactionRequest;
import com.eventledger.shared.TransactionResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request) {
        if (!accountId.equals(request.accountId())) {
            return ResponseEntity.badRequest().body(
                TransactionResponse.error(accountId, "Path accountId does not match request body"));
        }
        try {
            TransactionResponse response = accountService.applyTransaction(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to apply transaction for account {}: {}", accountId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(TransactionResponse.error(accountId, "An internal error occurred"));
        }
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String accountId) {
        try {
            AccountDetails details = accountService.getAccountDetails(accountId);
            return ResponseEntity.ok(Map.of(
                "accountId", details.accountId(),
                "balance", details.balance(),
                "currency", details.currency()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<?> getAccountDetails(@PathVariable String accountId) {
        try {
            AccountDetails details = accountService.getAccountDetails(accountId);
            return ResponseEntity.ok(details);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
