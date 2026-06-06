package com.ledger.gateway.service;

import com.ledger.common.dto.TransactionRequest;

public interface AccountClient {
    void sendTransaction(String accountId, TransactionRequest transactionRequest);
}
