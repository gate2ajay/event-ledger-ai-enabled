package com.ledger.account.dto;

import com.ledger.common.dto.TransactionRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetailsResponse {
    private String accountId;
    private BigDecimal balance;
    private String currency;
    private Instant lastUpdated;
    private List<TransactionRequest> transactions;
}
