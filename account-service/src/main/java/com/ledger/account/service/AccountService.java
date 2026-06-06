package com.ledger.account.service;

import com.ledger.common.dto.AccountBalanceResponse;
import com.ledger.common.dto.TransactionRequest;

import java.util.List;

public interface AccountService {
    void processTransaction(String accountId, TransactionRequest transactionRequest);
    AccountBalanceResponse getBalance(String accountId);
    List<TransactionRequest> getRecentTransactions(String accountId);
}
