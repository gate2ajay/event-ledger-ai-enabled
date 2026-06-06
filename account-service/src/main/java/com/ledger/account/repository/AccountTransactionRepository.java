package com.ledger.account.repository;

import com.ledger.account.domain.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, String> {
    List<AccountTransaction> findByAccountId(String accountId);
    List<AccountTransaction> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
