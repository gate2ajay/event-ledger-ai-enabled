package com.ledger.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.common.dto.EventPayload;
import com.ledger.common.dto.TransactionRequest;
import com.ledger.gateway.domain.GatewayEvent;
import com.ledger.gateway.exception.DuplicateEventException;
import com.ledger.gateway.repository.GatewayEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EventServiceTest {

    private GatewayEventRepository repository;
    private AccountClient accountClient;
    private ObjectMapper objectMapper;
    private EventService eventService;

    @BeforeEach
    public void setUp() {
        repository = Mockito.mock(GatewayEventRepository.class);
        accountClient = Mockito.mock(AccountClient.class);
        objectMapper = new ObjectMapper();
        eventService = new EventServiceImpl(repository, accountClient, objectMapper);
    }

    @Test
    public void testProcessEvent_Success() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.empty());

        EventPayload result = eventService.processEvent(payload);

        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo("evt-001");

        // Verify that the event is saved in both PENDING and COMPLETED states
        ArgumentCaptor<GatewayEvent> eventCaptor = ArgumentCaptor.forClass(GatewayEvent.class);
        verify(repository, times(1)).saveAndFlush(eventCaptor.capture());
        verify(repository, times(1)).save(eventCaptor.capture());
        
        // Verify that the final status of the saved event is COMPLETED
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("COMPLETED");

        // Verify account client was called
        verify(accountClient, times(1)).sendTransaction(eq("acct-123"), any(TransactionRequest.class));
    }

    @Test
    public void testProcessEvent_IdempotencyHit() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        GatewayEvent existingEvent = GatewayEvent.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .status("COMPLETED")
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.of(existingEvent));

        assertThatThrownBy(() -> eventService.processEvent(payload))
                .isInstanceOf(DuplicateEventException.class);

        verify(repository, never()).saveAndFlush(any(GatewayEvent.class));
        verify(repository, never()).save(any(GatewayEvent.class));
        verify(accountClient, never()).sendTransaction(anyString(), any(TransactionRequest.class));
    }

    @Test
    public void testProcessEvent_PropagationFailure() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Account Service down")).when(accountClient).sendTransaction(anyString(), any(TransactionRequest.class));

        assertThatThrownBy(() -> eventService.processEvent(payload))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<GatewayEvent> eventCaptor = ArgumentCaptor.forClass(GatewayEvent.class);
        verify(repository, times(1)).saveAndFlush(eventCaptor.capture());
        verify(repository, times(1)).save(eventCaptor.capture());

        // Second save status is FAILED
        assertThat(eventCaptor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
    }

    @Test
    public void testProcessEvent_WithMetadata() {
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("key1", "val1");
        meta.put("key2", 42);

        EventPayload payload = EventPayload.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadata(meta)
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.empty());

        EventPayload result = eventService.processEvent(payload);

        assertThat(result).isNotNull();
        assertThat(result.getMetadata()).containsEntry("key1", "val1");
        assertThat(result.getMetadata()).containsEntry("key2", 42);
    }

    @Test
    public void testProcessEvent_MetadataSerializationError() {
        // Mock ObjectMapper to throw exception during writeValueAsString
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        try {
            when(brokenMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Serialization failed"));
        } catch (Exception e) {}

        EventService serviceWithBrokenMapper = new EventServiceImpl(repository, accountClient, brokenMapper);

        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        meta.put("key", "val");

        EventPayload payload = EventPayload.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadata(meta)
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.empty());

        EventPayload result = serviceWithBrokenMapper.processEvent(payload);

        assertThat(result).isNotNull();
        assertThat(result.getMetadata()).isEmpty(); // Fallback to empty map/null due to catch block
    }

    @Test
    public void testProcessEvent_MetadataDeserializationError() {
        // Prepare repository to return a GatewayEvent with invalid JSON in metadataJson
        GatewayEvent existingEvent = GatewayEvent.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadataJson("{invalid-json}")
                .status("COMPLETED")
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.of(existingEvent));

        // Attempting to process this duplicate event will throw DuplicateEventException containing mapped payload
        assertThatThrownBy(() -> eventService.processEvent(EventPayload.builder().eventId("evt-001").build()))
                .isInstanceOf(DuplicateEventException.class)
                .satisfies(e -> {
                    DuplicateEventException ex = (DuplicateEventException) e;
                    assertThat(ex.getOriginalEvent().getMetadata()).isEmpty(); // Caught exception and returned empty map
                });
    }

    @Test
    public void testGetEventById_Found() {
        GatewayEvent existingEvent = GatewayEvent.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadataJson("{\"key\":\"val\"}")
                .status("COMPLETED")
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.of(existingEvent));

        EventPayload result = eventService.getEventById("evt-001");

        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo("evt-001");
        assertThat(result.getMetadata()).containsEntry("key", "val");
    }

    @Test
    public void testGetEventById_NotFound() {
        when(repository.findById("evt-not-exists")).thenReturn(Optional.empty());

        EventPayload result = eventService.getEventById("evt-not-exists");

        assertThat(result).isNull();
    }

    @Test
    public void testGetEventsByAccount() {
        GatewayEvent existingEvent = GatewayEvent.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .metadataJson("{\"key\":\"val\"}")
                .status("COMPLETED")
                .build();

        when(repository.findByAccountIdOrderByEventTimestampAsc("acct-123"))
                .thenReturn(java.util.List.of(existingEvent));

        java.util.List<EventPayload> results = eventService.getEventsByAccount("acct-123");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEventId()).isEqualTo("evt-001");
    }

    @Test
    public void testProcessEvent_DataIntegrityViolationOnSave() {
        EventPayload payload = EventPayload.builder()
                .eventId("evt-001")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        when(repository.findById("evt-001")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));

        assertThatThrownBy(() -> eventService.processEvent(payload))
                .isInstanceOf(DuplicateEventException.class);
    }
}
