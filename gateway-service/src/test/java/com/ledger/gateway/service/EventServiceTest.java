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
        verify(repository, times(2)).save(eventCaptor.capture());
        
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
        verify(repository, times(2)).save(eventCaptor.capture());

        // Second save status is FAILED
        assertThat(eventCaptor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
    }
}
