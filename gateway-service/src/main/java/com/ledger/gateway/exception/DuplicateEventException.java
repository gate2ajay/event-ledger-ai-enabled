package com.ledger.gateway.exception;

import com.ledger.common.dto.EventPayload;

public class DuplicateEventException extends RuntimeException {
    
    private final EventPayload originalEvent;

    public DuplicateEventException(EventPayload originalEvent) {
        super("Duplicate event submission: " + originalEvent.getEventId());
        this.originalEvent = originalEvent;
    }

    public EventPayload getOriginalEvent() {
        return originalEvent;
    }
}
