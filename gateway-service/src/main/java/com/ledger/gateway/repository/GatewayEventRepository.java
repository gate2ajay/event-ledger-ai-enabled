package com.ledger.gateway.repository;

import com.ledger.gateway.domain.GatewayEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatewayEventRepository extends JpaRepository<GatewayEvent, String> {
    List<GatewayEvent> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
