package io.github.emanoeladolpho.chassis.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventEntity.Key.class)
public class ProcessedEventEntity implements Persistable<ProcessedEventEntity.Key> {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(length = 100)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Sempre persist (nunca merge): um registro duplicado deve violar a chave primária. */
    @Transient
    private boolean isNew = true;

    protected ProcessedEventEntity() {
    }

    ProcessedEventEntity(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = Instant.now();
    }

    @Override
    public Key getId() {
        return new Key(eventId, consumer);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public record Key(UUID eventId, String consumer) implements Serializable {

        public Key() {
            this(null, null);
        }
    }
}
