package com.ledger.gateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.common.aop.AuditedTransaction;
import com.ledger.common.aop.TrackExecutionTime;
import com.ledger.common.dto.EventPayload;
import com.ledger.common.dto.TransactionRequest;
import com.ledger.gateway.domain.GatewayEvent;
import com.ledger.gateway.exception.DuplicateEventException;
import com.ledger.gateway.repository.GatewayEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EventServiceImpl implements EventService {

    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);

    private final GatewayEventRepository repository;
    private final AccountClient accountClient;
    private final ObjectMapper objectMapper;

    public EventServiceImpl(GatewayEventRepository repository, AccountClient accountClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.accountClient = accountClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    @TrackExecutionTime("processEvent")
    @AuditedTransaction(
            action = "GATEWAY_PROCESS_EVENT",
            eventId = "#eventPayload.eventId",
            accountId = "#eventPayload.accountId",
            type = "#eventPayload.type",
            amount = "#eventPayload.amount"
    )
    public EventPayload processEvent(EventPayload eventPayload) {
        log.info("Processing event: {}", eventPayload.getEventId());

        // 1. Check idempotency
        Optional<GatewayEvent> existing = repository.findById(eventPayload.getEventId());
        if (existing.isPresent()) {
            log.info("Duplicate event detected: {}", eventPayload.getEventId());
            throw new DuplicateEventException(mapToPayload(existing.get()));
        }

        // 2. Persist in PENDING state
        GatewayEvent event = GatewayEvent.builder()
                .eventId(eventPayload.getEventId())
                .accountId(eventPayload.getAccountId())
                .type(eventPayload.getType())
                .amount(eventPayload.getAmount())
                .currency(eventPayload.getCurrency())
                .eventTimestamp(eventPayload.getEventTimestamp())
                .metadataJson(serializeMetadata(eventPayload.getMetadata()))
                .status("PENDING")
                .build();
        try {
            repository.saveAndFlush(event);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Concurrency duplicate event detected via DB constraint: {}", eventPayload.getEventId());
            throw new DuplicateEventException(eventPayload);
        }

        try {
            // 3. Propagate to Account Service
            TransactionRequest request = TransactionRequest.builder()
                    .eventId(eventPayload.getEventId())
                    .type(eventPayload.getType())
                    .amount(eventPayload.getAmount())
                    .currency(eventPayload.getCurrency())
                    .eventTimestamp(eventPayload.getEventTimestamp())
                    .build();

            accountClient.sendTransaction(eventPayload.getAccountId(), request);

            // 4. Update status to COMPLETED
            event.setStatus("COMPLETED");
            repository.save(event);
            
            return mapToPayload(event);
        } catch (Exception e) {
            log.error("Failed to propagate event to Account Service: {}", eventPayload.getEventId(), e);
            event.setStatus("FAILED");
            repository.save(event);
            throw e; // Reraise exception for transaction rollback and circuit-breaker/global exception mapping
        }
    }

    @Override
    @TrackExecutionTime("getEventById")
    public EventPayload getEventById(String eventId) {
        return repository.findById(eventId)
                .map(this::mapToPayload)
                .orElse(null);
    }

    @Override
    @TrackExecutionTime("getEventsByAccount")
    public List<EventPayload> getEventsByAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId).stream()
                .map(this::mapToPayload)
                .collect(Collectors.toList());
    }

    private EventPayload mapToPayload(GatewayEvent event) {
        return EventPayload.builder()
                .eventId(event.getEventId())
                .accountId(event.getAccountId())
                .type(event.getType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .eventTimestamp(event.getEventTimestamp())
                .metadata(deserializeMetadata(event.getMetadataJson()))
                .build();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize metadata", e);
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize metadata", e);
            return Collections.emptyMap();
        }
    }
}
