package com.ledger.account.domain;

import com.ledger.account.dto.AccountDetailsResponse;
import com.ledger.common.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;

public class AccountPojoTest {

    @Test
    public void testAccountTransaction() {
        Instant now = Instant.now();
        AccountTransaction tx1 = new AccountTransaction();
        tx1.setEventId("evt-1");
        tx1.setAccountId("acct-1");
        tx1.setType("CREDIT");
        tx1.setAmount(BigDecimal.TEN);
        tx1.setCurrency("USD");
        tx1.setEventTimestamp(now);

        assertThat(tx1.getEventId()).isEqualTo("evt-1");
        assertThat(tx1.getAccountId()).isEqualTo("acct-1");
        assertThat(tx1.getType()).isEqualTo("CREDIT");
        assertThat(tx1.getAmount()).isEqualTo(BigDecimal.TEN);
        assertThat(tx1.getCurrency()).isEqualTo("USD");
        assertThat(tx1.getEventTimestamp()).isEqualTo(now);

        AccountTransaction tx2 = AccountTransaction.builder()
                .eventId("evt-1")
                .accountId("acct-1")
                .type("CREDIT")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(now)
                .build();

        AccountTransaction tx3 = AccountTransaction.builder()
                .eventId("evt-2")
                .accountId("acct-1")
                .type("CREDIT")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(now)
                .build();

        assertThat(tx1).isEqualTo(tx2);
        assertThat(tx1).isNotEqualTo(tx3);
        assertThat(tx1).isNotEqualTo(null);
        assertThat(tx1.hashCode()).isEqualTo(tx2.hashCode());
        assertThat(tx1.toString()).contains("evt-1");

        AccountTransaction allArgs = new AccountTransaction("evt-1", "acct-1", "CREDIT", BigDecimal.TEN, "USD", now);
        assertThat(allArgs.getEventId()).isEqualTo("evt-1");
    }

    @Test
    public void testAccountDetailsResponse() {
        Instant now = Instant.now();
        AccountDetailsResponse resp1 = new AccountDetailsResponse();
        resp1.setAccountId("acct-1");
        resp1.setBalance(BigDecimal.ONE);
        resp1.setCurrency("EUR");
        resp1.setLastUpdated(now);
        resp1.setTransactions(Collections.emptyList());

        assertThat(resp1.getAccountId()).isEqualTo("acct-1");
        assertThat(resp1.getBalance()).isEqualTo(BigDecimal.ONE);
        assertThat(resp1.getCurrency()).isEqualTo("EUR");
        assertThat(resp1.getLastUpdated()).isEqualTo(now);
        assertThat(resp1.getTransactions()).isEmpty();

        AccountDetailsResponse resp2 = AccountDetailsResponse.builder()
                .accountId("acct-1")
                .balance(BigDecimal.ONE)
                .currency("EUR")
                .lastUpdated(now)
                .transactions(Collections.emptyList())
                .build();

        AccountDetailsResponse resp3 = AccountDetailsResponse.builder()
                .accountId("acct-2")
                .balance(BigDecimal.ONE)
                .currency("EUR")
                .lastUpdated(now)
                .transactions(Collections.emptyList())
                .build();

        assertThat(resp1).isEqualTo(resp2);
        assertThat(resp1).isNotEqualTo(resp3);
        assertThat(resp1).isNotEqualTo(null);
        assertThat(resp1.hashCode()).isEqualTo(resp2.hashCode());
        assertThat(resp1.toString()).contains("acct-1");

        AccountDetailsResponse allArgs = new AccountDetailsResponse("acct-1", BigDecimal.ONE, "EUR", now, Collections.emptyList());
        assertThat(allArgs.getAccountId()).isEqualTo("acct-1");
    }
}
