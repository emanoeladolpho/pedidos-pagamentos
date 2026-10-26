# ADR 0004 — Transactional Outbox com relay por polling

**Status:** aceita

## Contexto

Ao criar um pedido é preciso gravar no banco **e** publicar `OrderCreated`. Fazer as duas coisas em sequência
(*dual write*) falha de formas silenciosas: commit sem publicação (pedido que nunca é cobrado) ou publicação sem
commit (cobrança de um pedido que não existe).

## Decisão

- O evento é gravado na tabela `outbox_events` **na mesma transação** da mudança de estado. O `OutboxWriter` exige
  transação ativa (`Propagation.MANDATORY`), então é impossível usá-lo fora dela.
- Um relay agendado lê lotes pendentes com `SELECT ... FOR UPDATE SKIP LOCKED` (várias instâncias não disputam as
  mesmas linhas), publica no Kafka aguardando o *ack* e marca `published_at`. Na primeira falha o lote para,
  preservando a ordem; o erro e o número de tentativas ficam registrados.
- A chave da mensagem é o id do pedido (ordenação por pedido, ver ADR 0005) e o correlation id vai como header.
- O produtor usa `acks=all` e `enable.idempotence=true`.

## Alternativas consideradas

- **CDC com Debezium** lendo o WAL: latência menor e sem polling, mas exige Kafka Connect e mais operação.
  O formato da tabela de outbox é compatível com o *Outbox Event Router* do Debezium, então a troca é possível.
- **Transações do Kafka** (`KafkaTransactionManager` + banco): não há atomicidade real entre os dois recursos.

## Consequências

- Garantia **at-least-once**: se o relay cair entre publicar e marcar, a mensagem é publicada de novo.
  Por isso os consumidores são idempotentes (ADR 0005).
- Latência adicional de até um intervalo de polling (500 ms por padrão).
- A métrica `outbox_pending_events` mostra se o relay está atrasado (Kafka fora, por exemplo).
- As linhas publicadas precisam de limpeza periódica (ver LIMITACOES.md).
