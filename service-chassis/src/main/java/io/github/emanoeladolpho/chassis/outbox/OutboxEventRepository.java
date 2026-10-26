package io.github.emanoeladolpho.chassis.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Próximos eventos pendentes, em ordem de criação. {@code FOR UPDATE SKIP LOCKED} permite várias
     * instâncias do serviço rodando o relay ao mesmo tempo sem publicar o mesmo evento duas vezes.
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextPending(@Param("limit") int limit);

    long countByPublishedAtIsNull();
}
