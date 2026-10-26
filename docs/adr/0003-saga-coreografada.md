# ADR 0003 — Saga coreografada por eventos em vez de orquestrador

**Status:** aceita

## Contexto

Criar um pedido e cobrá-lo envolve dois serviços e dois bancos. Não há transação distribuída (2PC não é suportado
pelo Kafka e acopla a disponibilidade dos participantes). É preciso uma saga: uma sequência de transações locais
com ações de compensação.

## Decisão

Saga **coreografada**: cada serviço reage a eventos do outro.

1. `OrderCreated` → payment-service cria o pagamento (`PaymentInitiated`) e cobra.
2. `PaymentApproved` → pedido `PAID`; `PaymentDeclined` → pedido `FAILED` (compensação: nada a desfazer).
3. `OrderCancelled` → se o pagamento foi aprovado, estorna (`PaymentRefunded`); se estava pendente, cancela.
4. Estorno manual do ADMIN → `PaymentRefunded` → pedido `CANCELLED`.

As transições são idempotentes e tolerantes a eventos fora de contexto (ex.: `PaymentApproved` para um pedido
já cancelado é ignorado).

## Alternativas consideradas

- **Orquestração** (um coordenador, ex.: Temporal, Camunda ou um serviço "saga"): fluxo explícito em um lugar e
  mais fácil de visualizar, mas adiciona um componente e é exagerado para dois participantes e quatro eventos.

## Consequências

- Baixo acoplamento: o order-service não conhece o gateway nem a lógica de cobrança.
- O fluxo fica distribuído no código dos dois serviços; os diagramas do README e os testes ponta a ponta
  documentam o todo.
- Com mais participantes (estoque, entrega), a coreografia tende a ficar difícil de seguir; seria o momento de
  reavaliar um orquestrador.
