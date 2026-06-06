package com.ledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions")
public class AccountTransaction {

    @Id
    @Column(name = "event_id", length = 50)
    private String eventId;

    @Column(name = "account_id", length = 50, nullable = false)
    private String accountId;

    @Column(name = "type", length = 10, nullable = false)
    private String type;

    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;
}
