package com.ledger.gateway.controller;

import com.ledger.common.dto.EventPayload;
import com.ledger.gateway.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventPayload> createEvent(@Valid @RequestBody EventPayload eventPayload) {
        EventPayload processed = eventService.processEvent(eventPayload);
        return ResponseEntity.status(HttpStatus.CREATED).body(processed);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventPayload> getEventById(@PathVariable("id") String id) {
        EventPayload event = eventService.getEventById(id);
        if (event == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(event);
    }

    @GetMapping
    public ResponseEntity<List<EventPayload>> getEventsByAccount(@RequestParam("account") String accountId) {
        List<EventPayload> events = eventService.getEventsByAccount(accountId);
        return ResponseEntity.ok(events);
    }
}
