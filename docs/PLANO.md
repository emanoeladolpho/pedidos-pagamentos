# Plano de implementação

Plataforma de pedidos e pagamentos com dois serviços independentes (`order-service` e `payment-service`),
Arquitetura Hexagonal, DDD tático e comunicação assíncrona via Kafka.

> Ordem ajustada durante a execução: a mensageria veio antes da API REST, porque os casos de uso só podem
> ser montados no Spring quando as portas de saída (outbox e inbox) já têm implementação.

Cada fase termina com **build verde** (`./mvnw verify`) e um ou mais commits (conventional commits).

---

## Visão geral da solução

```
pedidos-pagamentos/
├── service-chassis/   # infraestrutura transversal reutilizável (outbox, inbox, correlation id, erros, segurança)
├── order-service/     # pedidos: domain / application / infrastructure
├── payment-service/   # pagamentos: domain / application / infrastructure
├── e2e-tests/         # teste ponta a ponta: os dois serviços + 2 Postgres + Kafka no mesmo teste
├── contracts/pacts/   # contratos (Pact) das mensagens trocadas entre os serviços
├── infra/             # Keycloak (realm), Prometheus, Grafana
└── docs/              # plano, ADRs, decisões, limitações
```

### Fluxo do pedido (saga coreografada)

1. `POST /orders` (com `Idempotency-Key`) → pedido `CREATED` + evento `OrderCreated` gravado no **outbox** na mesma transação.
2. O relay do outbox publica `OrderCreated` no tópico `order-events`.
3. `payment-service` consome (idempotente), cria o pagamento e publica `PaymentInitiated` → pedido vai para `PAYMENT_PENDING`.
4. `payment-service` chama o gateway simulado (Resilience4j: timeout, retry, circuit breaker).
   - aprovado → `PaymentApproved` → pedido `PAID`
   - recusado → `PaymentDeclined` → pedido `FAILED`
   - gateway indisponível após as tentativas → mensagem vai para a **DLQ** (`order-events-dlt`)
5. Cancelamento: `POST /orders/{id}/cancel` → `CANCELLED` + `OrderCancelled`; se o pagamento estava aprovado,
   o `payment-service` estorna e publica `PaymentRefunded`.

### Ciclos de vida

```
Pedido:     CREATED → PAYMENT_PENDING → PAID
                 └──────────┴──────────→ FAILED
            CREATED | PAYMENT_PENDING | PAID → CANCELLED

Pagamento:  PENDING → APPROVED → REFUNDED
                 └──→ DECLINED
```

---

## Fases

### Fase 0 — Esqueleto
- POM pai multi-módulo (Java 21, Spring Boot 3.5, gerenciamento de versões), Maven Wrapper, `.gitignore`.
- Módulos vazios compilando.
- **Pronto quando:** `./mvnw verify` passa.

### Fase 1 — Domínio (sem framework)
- `order-service/domain`: `Order` (aggregate root), `OrderItem`, value objects (`OrderId`, `CustomerId`, `Money`, `Quantity`, `Sku`),
  `OrderStatus` com transições, eventos `OrderCreated` e `OrderCancelled`.
- `payment-service/domain`: `Payment` (aggregate root), value objects, `PaymentStatus`, eventos `PaymentInitiated`,
  `PaymentApproved`, `PaymentDeclined` e `PaymentRefunded`.
- Testes unitários puros (JUnit 5 + AssertJ, sem Spring).
- **Pronto quando:** testes de domínio passam e o domínio não importa nada de Spring/JPA.

### Fase 2 — Casos de uso e portas
- Portas de entrada (um caso de uso por interface/classe) e de saída (`OrderRepository`, `EventPublisher`,
  `IdempotencyStore`, `ProcessedEventStore`, `UnitOfWork`, `PaymentGateway`...).
- Testes dos casos de uso com mocks das portas (Mockito).
- Testes **ArchUnit** que quebram o build se as dependências não apontarem para dentro.
- **Pronto quando:** testes de casos de uso e de arquitetura passam.

### Fase 3 — Persistência
- Adapters JPA + mapeamento domínio ↔ entidade com MapStruct.
- Liquibase por serviço; um PostgreSQL por serviço.
- `UnitOfWork` implementado com `TransactionTemplate`.
- Testes de integração com Testcontainers (PostgreSQL).

### Fase 4 — Mensageria
- `service-chassis`: Transactional Outbox (tabela + relay com `FOR UPDATE SKIP LOCKED`) e inbox de eventos processados.
- Produtores/consumidores Kafka, envelope de evento versionado, chave de partição = id do pedido.
- Consumidores idempotentes; `DefaultErrorHandler` com backoff e Dead Letter Topic.
- **Testes de contrato** com Pact (mensagens) entre os serviços.
- Testes de integração com Testcontainers (PostgreSQL + Kafka).

### Fase 5 — API REST
- Controllers (adapters de entrada), DTOs, validação, `ProblemDetail` (RFC 7807/9457), OpenAPI.
- `Idempotency-Key` obrigatório na criação de pedido (replay devolve a mesma resposta; payload diferente → 422).

### Fase 6 — Segurança
- Keycloak (realm importado no compose), resource server JWT, roles `USER`/`ADMIN` a partir de `realm_access.roles`.
- Regras: usuário vê/cancela só os próprios pedidos; estorno manual só ADMIN.

### Fase 7 — Resiliência
- Gateway de pagamento simulado (latência, taxa de falha técnica e taxa de recusa configuráveis).
- Resilience4j: timeout, retry com backoff exponencial e circuit breaker.

### Fase 8 — Observabilidade
- Logs JSON (structured logging do Spring Boot) com `correlationId` no MDC, propagado HTTP → outbox → Kafka → outro serviço.
- Micrometer + Prometheus (métricas de negócio, HTTP, Resilience4j, outbox) e dashboard no Grafana.
- Health checks (liveness/readiness).

### Fase 9 — Testes ponta a ponta e cobertura
- Módulo `e2e-tests`: sobe os dois serviços no mesmo teste com Testcontainers e valida o fluxo completo
  (pedido → pagamento → `PAID`; cancelamento → estorno).
- JaCoCo com mínimo de 80% de cobertura de linhas por módulo, verificado no `verify`.

### Fase 10 — Containers
- Dockerfile multi-stage por serviço.
- `docker-compose.yml` sobe tudo: 2 serviços, 2 PostgreSQL, Kafka, Keycloak, Prometheus e Grafana.
- `.env.example` com todas as variáveis; nenhuma credencial versionada.
- Script `scripts/fluxo-completo.sh` que executa o fluxo de um pedido contra o ambiente.

### Fase 11 — CI
- GitHub Actions em push e PR: build, testes, cobertura (JaCoCo), verificação de contratos atualizados e build das imagens.

### Fase 12 — Documentação
- README em português e inglês com diagramas Mermaid (arquitetura e sequência).
- `docs/adr/`: ADRs das decisões relevantes.
- `docs/DECISOES.md`: roteiro de perguntas e respostas de entrevista.
- `docs/LIMITACOES.md`: o que ficou fora do escopo e como seria feito.
