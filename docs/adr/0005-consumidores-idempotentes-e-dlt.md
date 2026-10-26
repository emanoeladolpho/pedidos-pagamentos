# ADR 0005 — Consumidores idempotentes (inbox), ordenação por chave e Dead Letter Topic

**Status:** aceita

## Contexto

Com entrega at-least-once (ADR 0004) e rebalanceamentos do Kafka, cada mensagem pode chegar mais de uma vez.
Algumas mensagens nunca serão processáveis (JSON inválido, pedido inexistente) e não podem travar a partição.

## Decisão

- **Inbox**: tabela `processed_events (event_id, consumer)`. O caso de uso verifica e registra o evento na mesma
  transação da mudança de estado. A inserção usa `persist`; se outra instância já registrou, a chave primária
  duplicada aborta a transação, em vez de sobrescrever em silêncio.
- No pagamento, o evento só é marcado como processado **no fim** (depois da cobrança). Uma reentrega reaproveita o
  pagamento `PENDING` existente, e o gateway é chamado com o id do pagamento como chave de idempotência.
- **Ordenação**: chave de partição = id do pedido, então `OrderCreated` e `OrderCancelled` do mesmo pedido são
  consumidos na ordem em que foram produzidos.
- **Erros**: `DefaultErrorHandler` com backoff exponencial; esgotadas as tentativas, a mensagem original vai para
  `<tópico>-dlt` (mesma partição), com headers de exceção. Mensagens malformadas (`MalformedEventException`)
  vão direto para a DLT, sem retry.
- Eventos de tipo desconhecido são ignorados (*tolerant reader*), não enviados para a DLT.

## Alternativas consideradas

- **Exactly-once do Kafka**: só vale dentro do Kafka (consume-transform-produce); não cobre o banco nem o gateway.
- **Idempotência "natural"** apenas pelo estado do agregado: funciona para algumas transições, mas não para todas
  (dois `OrderCancelled` diferentes, por exemplo). O inbox é explícito e uniforme.
- **Tópicos de retry não bloqueantes** (`@RetryableTopic`): não bloqueiam a partição, mas quebram a ordem por
  pedido, que aqui é importante.

## Consequências

- Retry bloqueante: uma mensagem com problema atrasa as seguintes da mesma partição durante o backoff (segundos).
- A DLT precisa de operação: inspeção e reenvio (ver LIMITACOES.md).
- A tabela de inbox cresce e precisa de expurgo por idade.
