package com.ledger.account.service;

import com.ledger.common.aop.AuditedTransaction;
import com.ledger.common.aop.TrackExecutionTime;
import com.ledger.common.dto.AccountBalanceResponse;
import com.ledger.common.dto.TransactionRequest;
import com.ledger.account.domain.AccountTransaction;
import com.ledger.account.exception.DuplicateTransactionException;
import com.ledger.account.repository.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);
    private final AccountTransactionRepository repository;

    public AccountServiceImpl(AccountTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    @TrackExecutionTime("processTransaction")
    @AuditedTransaction(
            action = "ACCOUNT_PROCESS_TRANSACTION",
            eventId = "#request.eventId",
            accountId = "#accountId",
            type = "#request.type",
            amount = "#request.amount"
    )
    public void processTransaction(String accountId, TransactionRequest request) {
        log.info("Processing transaction for account: {}, eventId: {}", accountId, request.getEventId());

        // Enforce idempotency check
        if (repository.existsById(request.getEventId())) {
            log.info("Duplicate transaction detected in Account Service: {}", request.getEventId());
            throw new DuplicateTransactionException(request.getEventId());
        }

        // Enforce currency consistency: get last transaction for this account and assert currency matches
        AccountTransaction lastTx = repository.findFirstByAccountIdOrderByEventTimestampDesc(accountId);
        if (lastTx != null && !lastTx.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            log.warn("Currency mismatch for account {}: expected {}, got {}", accountId, lastTx.getCurrency(), request.getCurrency());
            throw new IllegalArgumentException("Currency mismatch for account: " + accountId + ". Expected " + lastTx.getCurrency() + ", got " + request.getCurrency());
        }

        // Persist transaction
        AccountTransaction transaction = AccountTransaction.builder()
                .eventId(request.getEventId())
                .accountId(accountId)
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .build();

        repository.save(transaction);
        log.info("Successfully persisted transaction: {}", request.getEventId());
    }

    @Override
    @Transactional(readOnly = true)
    @TrackExecutionTime("getBalance")
    public AccountBalanceResponse getBalance(String accountId) {
        log.info("Calculating balance dynamically for account: {}", accountId);
        
        BigDecimal balance = repository.calculateBalance(accountId);
        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        AccountTransaction lastTx = repository.findFirstByAccountIdOrderByEventTimestampDesc(accountId);
        Instant lastUpdated = (lastTx != null) ? lastTx.getEventTimestamp() : Instant.now();
        String currency = (lastTx != null) ? lastTx.getCurrency() : "USD";

        return AccountBalanceResponse.builder()
                .accountId(accountId)
                .balance(balance)
                .currency(currency)
                .lastUpdated(lastUpdated)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @TrackExecutionTime("getRecentTransactions")
    public List<TransactionRequest> getRecentTransactions(String accountId) {
        log.info("Fetching chronological transactions for account: {}", accountId);
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId).stream()
                .map(tx -> TransactionRequest.builder()
                        .eventId(tx.getEventId())
                        .type(tx.getType())
                        .amount(tx.getAmount())
                        .currency(tx.getCurrency())
                        .eventTimestamp(tx.getEventTimestamp())
                        .build())
                .collect(Collectors.toList());
    }
}
