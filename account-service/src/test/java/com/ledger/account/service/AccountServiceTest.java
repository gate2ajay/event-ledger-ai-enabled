package com.ledger.account.service;

import com.ledger.common.dto.AccountBalanceResponse;
import com.ledger.common.dto.TransactionRequest;
import com.ledger.account.domain.AccountTransaction;
import com.ledger.account.exception.DuplicateTransactionException;
import com.ledger.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class AccountServiceTest {

    private AccountTransactionRepository repository;
    private AccountService accountService;

    @BeforeEach
    public void setUp() {
        repository = Mockito.mock(AccountTransactionRepository.class);
        accountService = new AccountServiceImpl(repository);
    }

    @Test
    public void testProcessTransaction_Success() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(repository.existsById("evt-001")).thenReturn(false);

        accountService.processTransaction("acct-123", request);

        verify(repository, times(1)).save(any(AccountTransaction.class));
    }

    @Test
    public void testProcessTransaction_IdempotencyHit() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(repository.existsById("evt-001")).thenReturn(true);

        assertThatThrownBy(() -> accountService.processTransaction("acct-123", request))
                .isInstanceOf(DuplicateTransactionException.class);

        verify(repository, never()).save(any(AccountTransaction.class));
    }

    @Test
    public void testProcessTransaction_CurrencyMismatch() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-002")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .eventTimestamp(Instant.now())
                .build();

        AccountTransaction lastTx = AccountTransaction.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(repository.existsById("evt-002")).thenReturn(false);
        when(repository.findFirstByAccountIdOrderByEventTimestampDesc("acct-123")).thenReturn(lastTx);

        assertThatThrownBy(() -> accountService.processTransaction("acct-123", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");

        verify(repository, never()).save(any(AccountTransaction.class));
    }

    @Test
    public void testGetBalance_CalculatesDynamically() {
        String accountId = "acct-123";
        Instant t2 = Instant.parse("2026-05-15T11:00:00Z");

        when(repository.calculateBalance(accountId)).thenReturn(new BigDecimal("120.00"));
        AccountTransaction latestTx = new AccountTransaction("evt-2", accountId, "DEBIT", new BigDecimal("30.00"), "USD", t2);
        when(repository.findFirstByAccountIdOrderByEventTimestampDesc(accountId)).thenReturn(latestTx);

        AccountBalanceResponse response = accountService.getBalance(accountId);

        assertThat(response.getBalance()).isEqualByComparingTo("120.00");
        assertThat(response.getLastUpdated()).isEqualTo(t2); // t2 is the latest chronologically
        assertThat(response.getCurrency()).isEqualTo("USD");
    }

    @Test
    public void testGetRecentTransactions_OrdersChronologically() {
        String accountId = "acct-123";
        Instant t1 = Instant.parse("2026-05-15T10:00:00Z");
        Instant t2 = Instant.parse("2026-05-15T11:00:00Z");
        Instant t3 = Instant.parse("2026-05-15T09:00:00Z");

        // The repository is assumed to sort by eventTimestamp asc (defined in query method name)
        List<AccountTransaction> dbSortedTx = Arrays.asList(
                new AccountTransaction("evt-3", accountId, "CREDIT", new BigDecimal("50.00"), "USD", t3),
                new AccountTransaction("evt-1", accountId, "CREDIT", new BigDecimal("100.00"), "USD", t1),
                new AccountTransaction("evt-2", accountId, "DEBIT", new BigDecimal("30.00"), "USD", t2)
        );

        when(repository.findByAccountIdOrderByEventTimestampAsc(accountId)).thenReturn(dbSortedTx);

        List<TransactionRequest> result = accountService.getRecentTransactions(accountId);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getEventId()).isEqualTo("evt-3");
        assertThat(result.get(1).getEventId()).isEqualTo("evt-1");
        assertThat(result.get(2).getEventId()).isEqualTo("evt-2");
    }
}
