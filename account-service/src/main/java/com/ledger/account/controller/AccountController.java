package com.ledger.account.controller;

import com.ledger.common.dto.AccountBalanceResponse;
import com.ledger.common.dto.TransactionRequest;
import com.ledger.account.dto.AccountDetailsResponse;
import com.ledger.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<Void> applyTransaction(
            @PathVariable("accountId") String accountId,
            @Valid @RequestBody TransactionRequest transactionRequest) {
        accountService.processTransaction(accountId, transactionRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable("accountId") String accountId) {
        AccountBalanceResponse balance = accountService.getBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailsResponse> getAccountDetails(@PathVariable("accountId") String accountId) {
        AccountBalanceResponse balance = accountService.getBalance(accountId);
        List<TransactionRequest> transactions = accountService.getRecentTransactions(accountId);
        
        AccountDetailsResponse response = AccountDetailsResponse.builder()
                .accountId(balance.getAccountId())
                .balance(balance.getBalance())
                .currency(balance.getCurrency())
                .lastUpdated(balance.getLastUpdated())
                .transactions(transactions)
                .build();
                
        return ResponseEntity.ok(response);
    }
}
