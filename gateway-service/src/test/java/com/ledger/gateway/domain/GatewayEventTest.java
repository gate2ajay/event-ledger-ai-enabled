package com.ledger.gateway.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

public class GatewayEventTest {

    @Test
    public void testGettersSettersAndConstructors() {
        Instant now = Instant.now();
        GatewayEvent event = new GatewayEvent();
        event.setEventId("evt-123");
        event.setAccountId("acct-123");
        event.setType("CREDIT");
        event.setAmount(BigDecimal.TEN);
        event.setCurrency("USD");
        event.setEventTimestamp(now);
        event.setMetadataJson("{}");
        event.setStatus("COMPLETED");

        assertThat(event.getEventId()).isEqualTo("evt-123");
        assertThat(event.getAccountId()).isEqualTo("acct-123");
        assertThat(event.getType()).isEqualTo("CREDIT");
        assertThat(event.getAmount()).isEqualTo(BigDecimal.TEN);
        assertThat(event.getCurrency()).isEqualTo("USD");
        assertThat(event.getEventTimestamp()).isEqualTo(now);
        assertThat(event.getMetadataJson()).isEqualTo("{}");
        assertThat(event.getStatus()).isEqualTo("COMPLETED");

        GatewayEvent allArgs = new GatewayEvent("evt-123", "acct-123", "CREDIT", BigDecimal.TEN, "USD", now, "{}", "COMPLETED");
        assertThat(allArgs.getEventId()).isEqualTo("evt-123");
    }

    @Test
    public void testEqualsAndHashCode() {
        Instant now = Instant.now();
        GatewayEvent event1 = GatewayEvent.builder()
                .eventId("evt-123")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(now)
                .metadataJson("{}")
                .status("COMPLETED")
                .build();

        GatewayEvent event2 = GatewayEvent.builder()
                .eventId("evt-123")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(now)
                .metadataJson("{}")
                .status("COMPLETED")
                .build();

        GatewayEvent event3 = GatewayEvent.builder()
                .eventId("evt-456")
                .accountId("acct-123")
                .type("CREDIT")
                .amount(BigDecimal.TEN)
                .currency("USD")
                .eventTimestamp(now)
                .metadataJson("{}")
                .status("COMPLETED")
                .build();

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
        assertThat(event1).isNotEqualTo(null);
        assertThat(event1).isNotEqualTo(new Object());
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
        assertThat(event1.hashCode()).isNotEqualTo(event3.hashCode());
    }

    @Test
    public void testToString() {
        GatewayEvent event = GatewayEvent.builder()
                .eventId("evt-123")
                .build();
        assertThat(event.toString()).contains("evt-123");
    }
}
