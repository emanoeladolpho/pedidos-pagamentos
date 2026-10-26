# Architecture Decision Records

Cada ADR registra uma decisão relevante: o contexto, o que foi decidido, as alternativas e as consequências
(inclusive as negativas). Formato inspirado no modelo de Michael Nygard.

| # | Decisão | Status |
|---|---|---|
| [0001](0001-arquitetura-hexagonal-e-ddd-tatico.md) | Arquitetura hexagonal com DDD tático, verificada por ArchUnit | Aceita |
| [0002](0002-banco-por-servico-e-liquibase.md) | Um banco PostgreSQL por serviço, migrations com Liquibase | Aceita |
| [0003](0003-saga-coreografada.md) | Saga coreografada por eventos em vez de orquestrador | Aceita |
| [0004](0004-transactional-outbox.md) | Transactional Outbox com relay por polling | Aceita |
| [0005](0005-consumidores-idempotentes-e-dlt.md) | Consumidores idempotentes (inbox) e Dead Letter Topic | Aceita |
| [0006](0006-idempotency-key-na-api.md) | `Idempotency-Key` na criação de pedidos | Aceita |
| [0007](0007-gateway-fora-da-transacao-e-resilience4j.md) | Gateway chamado fora da transação, protegido por Resilience4j | Aceita |
| [0008](0008-contratos-de-mensagem-com-pact.md) | Envelope versionado e contratos de mensagem com Pact | Aceita |
| [0009](0009-keycloak-e-jwt.md) | Keycloak como provedor de identidade e JWT validado localmente | Aceita |
| [0010](0010-service-chassis-e-observabilidade.md) | Service chassis compartilhado e correlation id próprio | Aceita |
