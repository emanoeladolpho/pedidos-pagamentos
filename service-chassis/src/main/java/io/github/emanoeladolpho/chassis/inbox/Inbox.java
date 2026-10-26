package io.github.emanoeladolpho.chassis.inbox;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registro dos eventos já processados por cada consumidor. Marcado na mesma transação do efeito
 * de negócio: se a transação falhar, o evento não fica marcado e será reprocessado.
 * A chave primária (event_id, consumer) protege contra duas instâncias processando o mesmo evento.
 */
public class Inbox {

    private final ProcessedEventRepository repository;

    public Inbox(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isProcessed(UUID eventId, String consumer) {
        return repository.existsById(new ProcessedEventEntity.Key(eventId, consumer));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(UUID eventId, String consumer) {
        // persist (e não merge): se outra instância já gravou, a PK duplicada aborta esta transação
        repository.saveAndFlush(new ProcessedEventEntity(eventId, consumer));
    }
}
