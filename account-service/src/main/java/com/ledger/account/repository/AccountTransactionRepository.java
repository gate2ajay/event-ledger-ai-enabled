package com.ledger.account.repository;

import com.ledger.account.domain.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, String> {
    List<AccountTransaction> findByAccountId(String accountId);
    List<AccountTransaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END), 0) " +
           "FROM AccountTransaction t WHERE t.accountId = :accountId")
    java.math.BigDecimal calculateBalance(@org.springframework.data.repository.query.Param("accountId") String accountId);

    AccountTransaction findFirstByAccountIdOrderByEventTimestampDesc(String accountId);
}
