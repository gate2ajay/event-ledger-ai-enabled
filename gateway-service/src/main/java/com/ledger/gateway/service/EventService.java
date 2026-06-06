package com.ledger.gateway.service;

import com.ledger.common.dto.EventPayload;

import java.util.List;

public interface EventService {
    EventPayload processEvent(EventPayload eventPayload);
    EventPayload getEventById(String eventId);
    List<EventPayload> getEventsByAccount(String accountId);
}
