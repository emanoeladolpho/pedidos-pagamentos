package io.github.emanoeladolpho.order.infrastructure.adapter.out.persistence;

import io.github.emanoeladolpho.order.application.port.out.ProcessedEventStore;
import io.github.emanoeladolpho.chassis.inbox.Inbox;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class InboxProcessedEventStore implements ProcessedEventStore {

    private final Inbox inbox;

    InboxProcessedEventStore(Inbox inbox) {
        this.inbox = inbox;
    }

    @Override
    public boolean isProcessed(UUID eventId, String consumer) {
        return inbox.isProcessed(eventId, consumer);
    }

    @Override
    public void markProcessed(UUID eventId, String consumer) {
        inbox.markProcessed(eventId, consumer);
    }
}
