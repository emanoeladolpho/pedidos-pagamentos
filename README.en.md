# Orders and Payments

[![CI](https://github.com/emanoeladolpho/pedidos-pagamentos/actions/workflows/ci.yml/badge.svg)](https://github.com/emanoeladolpho/pedidos-pagamentos/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![Kafka](https://img.shields.io/badge/Kafka-3.9-black)
![Coverage](https://img.shields.io/badge/coverage-%E2%89%A580%25-success)

[🇧🇷 Português](README.md) | 🇺🇸 English

An **order and payment** platform made of two independent microservices that talk through Kafka events.
It uses a small, well-known domain to show the decisions that show up in real distributed systems: consistency
without distributed transactions, at-least-once delivery, idempotency, partial failures, contracts between services
and observability.

- **order-service**: places orders (with an `Idempotency-Key`), computes totals, drives the lifecycle
  (`CREATED → PAYMENT_PENDING → PAID`, `FAILED`, `CANCELLED`), cancels and queries.
- **payment-service**: charges each order on a simulated gateway (configurable failures and declines),
  handles refunds and queries.

> Code identifiers, logs and the remaining docs (ADRs, Q&A, limitations) are written in Portuguese.

## Highlights

| Topic | Approach |
|---|---|
| Architecture | Hexagonal + Clean Architecture + tactical DDD; domain and use cases **free of Spring/JPA**, enforced by **ArchUnit** |
| Cross-service consistency | Choreographed saga; **Transactional Outbox** (`FOR UPDATE SKIP LOCKED`) instead of dual writes |
| Duplicate delivery | **Idempotent consumers** (`processed_events` table) and gateway calls with an idempotency key |
| Repeated requests | **`Idempotency-Key`** header on order creation (replay returns the same order; different body → 422) |
| Provider failures | **Resilience4j** (timeout, retry with backoff, circuit breaker) + **Dead Letter Topic** |
| Event ordering | Partition key = order id |
| Contracts | **Pact** message contracts in both directions, versioned and checked in CI |
| Security | **Keycloak** (OAuth2/OIDC), JWT validated locally, `USER` and `ADMIN` roles |
| Errors | **Problem Details** (RFC 9457) with `code`, `timestamp` and `correlationId` on every error response |
| Observability | JSON logs with a **correlation id propagated HTTP → outbox → Kafka → other service**; Micrometer + Prometheus + Grafana |
| Quality | 185 tests: domain, use cases, ArchUnit, Testcontainers, contract and end-to-end; **JaCoCo ≥ 80%** enforced by the build |

## Architecture

```mermaid
flowchart LR
    client([HTTP client])
    kc[Keycloak<br/>OAuth2 / JWT]

    subgraph order[order-service]
        direction TB
        ow[REST adapter] --> ouc[Use cases] --> od[Domain<br/>Order]
        ouc --> op[JPA adapter]
        ouc --> oob[Outbox writer]
        olis[Kafka listener] --> ouc
        orelay[Outbox relay]
    end

    subgraph payment[payment-service]
        direction TB
        pw[REST adapter] --> puc[Use cases] --> pd[Domain<br/>Payment]
        puc --> pp[JPA adapter]
        puc --> pob[Outbox writer]
        puc --> pg[Gateway adapter<br/>Resilience4j]
        plis[Kafka listener] --> puc
        prelay[Outbox relay]
    end

    odb[(PostgreSQL<br/>orders)]
    pdb[(PostgreSQL<br/>payments)]
    gw{{Simulated<br/>payment gateway}}

    subgraph kafka[Kafka]
        t1[[order-events]]
        t2[[payment-events]]
        d1[[order-events-dlt]]
        d2[[payment-events-dlt]]
    end

    client -->|token| kc
    client -->|Bearer JWT| ow
    client -->|Bearer JWT| pw
    op & oob --> odb
    orelay --> odb
    pp & pob --> pdb
    prelay --> pdb
    orelay --> t1 --> plis
    prelay --> t2 --> olis
    plis -. failure after retries .-> d1
    olis -. failure after retries .-> d2
    pg --> gw

    prom[Prometheus] -.-> order & payment
    graf[Grafana] --> prom
```

Both services share the same layering, with dependencies always pointing inwards:

```
order-service/src/main/java/io/github/emanoeladolpho/order
├── domain/            # Order, OrderItem, value objects, events — plain Java
├── application/
│   ├── port/in/       # one use case per interface (PlaceOrderUseCase, CancelOrderUseCase...)
│   ├── port/out/      # what the application needs from the outside (OrderRepository, UnitOfWork, ...)
│   └── usecase/       # implementations — no framework annotations
└── infrastructure/
    ├── adapter/in/    # web (REST) and messaging (Kafka listener)
    ├── adapter/out/   # persistence (JPA + MapStruct) and messaging (outbox)
    └── config/        # composition root: wires ports to adapters
```

The **`service-chassis`** module holds the cross-cutting infrastructure that belongs to no domain: outbox, inbox,
Kafka error handling (retry + DLT), correlation id, Problem Details and JWT validation. It is a Spring Boot
auto-configuration: a service only needs to declare the dependency.

## Order flow

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant O as order-service
    participant ODB as orders DB
    participant K as Kafka
    participant P as payment-service
    participant PDB as payments DB
    participant G as Gateway (simulated)

    C->>O: POST /orders (Bearer JWT, Idempotency-Key)
    O->>ODB: one transaction: order CREATED + idempotency key + OrderCreated in the outbox
    O-->>C: 201 Created (status CREATED)
    O->>K: outbox relay publishes OrderCreated (key = orderId)
    K->>P: OrderCreated
    P->>PDB: tx1: payment PENDING + PaymentInitiated in the outbox
    P->>G: charge (timeout + retry + circuit breaker), outside any transaction
    G-->>P: approved / declined
    P->>PDB: tx2: APPROVED or DECLINED + event in the outbox + event marked as processed
    P->>K: relay publishes PaymentInitiated, PaymentApproved/Declined
    K->>O: payment events
    O->>ODB: PAYMENT_PENDING → PAID (or FAILED), event marked as processed
    C->>O: GET /orders/{id}
    O-->>C: 200 (status PAID)

    Note over C,G: Cancelling a paid order: OrderCancelled → payment-service refunds → PaymentRefunded
    Note over P,K: Gateway down after all attempts → message goes to order-events-dlt (payment stays PENDING)
```

## Running it

Requirements: Docker with Docker Compose. For the tests, Java 21 (the Maven Wrapper is included).

```bash
cp .env.example .env          # change the passwords
docker compose up -d --build  # 2 services, 2 PostgreSQL, Kafka, Keycloak, Prometheus and Grafana
./scripts/fluxo-completo.sh   # runs the whole flow end to end (needs curl and python3)
```

| Service | Address |
|---|---|
| order-service (Swagger) | http://localhost:8081/swagger-ui.html |
| payment-service (Swagger) | http://localhost:8082/swagger-ui.html |
| Keycloak (realm `pedidos`) | http://localhost:8080 (port set by `KEYCLOAK_PORT`) |
| Grafana (*Pedidos e Pagamentos* dashboard) | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

Demo users: `cliente` (role `USER`) and `admin` (roles `USER` and `ADMIN`), with the passwords set in
`KC_DEMO_USER_PASSWORD` and `KC_DEMO_ADMIN_PASSWORD`.

The simulated gateway accepts "magic" amounts for deterministic tests: a total ending in **.13** is declined and
**.66** simulates a provider outage. `GATEWAY_FAILURE_RATE` and `GATEWAY_DECLINE_RATE` add random failures on top.

### Tests

```bash
./mvnw test     # unit, use case, ArchUnit and contract tests (no Docker)
./mvnw verify   # + Testcontainers integration, end-to-end and the 80% coverage gate (needs Docker)
```

| Kind | Where | What it guarantees |
|---|---|---|
| Domain | `*/domain/*Test` | rules and state transitions, no framework |
| Use cases | `*/application/*Test` | orchestration with mocked ports (idempotency, replay, refunds...) |
| Architecture | `HexagonalArchitectureTest` | inward dependencies; domain/application free of Spring/JPA |
| Web | `*ControllerTest` | validation, HTTP status codes, Problem Details, access rules |
| Integration | `*IT` | JPA + Liquibase on real PostgreSQL; outbox → Kafka; consumers; DLT |
| Contract | `*PactTest` | messages produced by one service satisfy what the other consumes |
| End-to-end | `OrderPaymentFlowE2EIT` | both real services, 2 databases, Kafka and signed JWTs: paid, declined, cancelled/refunded |

## Request examples

```bash
TOKEN=$(curl -s localhost:8080/realms/pedidos/protocol/openid-connect/token \
  -d grant_type=password -d client_id=pedidos-api -d username=cliente -d password="$KC_DEMO_USER_PASSWORD" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

# Place an order
curl -s localhost:8081/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -H 'X-Correlation-Id: demo-123' \
  -d '{"currency":"BRL","items":[{"sku":"TECLADO-MEC-01","productName":"Mechanical keyboard","unitPrice":349.90,"quantity":1}]}'

# Order and payment
curl -s localhost:8081/orders/{id} -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8082/payments?orderId={id}" -H "Authorization: Bearer $TOKEN"

# Cancel (refunds if already paid)
curl -s -X POST localhost:8081/orders/{id}/cancel -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"reason":"Changed my mind"}'
```

### Endpoints

| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/orders` | USER | Place an order (`Idempotency-Key` header required) |
| GET | `/orders/{id}` | USER | Get an order (customers see only their own; ADMIN sees all) |
| GET | `/orders?page=&size=` | USER | Paginated list |
| POST | `/orders/{id}/cancel` | USER | Cancel; triggers a refund if paid |
| GET | `/payments/{id}` | USER | Get a payment |
| GET | `/payments?orderId=` | USER | Payment of an order |
| POST | `/payments/{id}/refund` | ADMIN | Manual refund (the order is cancelled afterwards) |

## Documentation (Portuguese)

- [docs/PLANO.md](docs/PLANO.md): phased implementation plan
- [docs/adr/](docs/adr/): architecture decision records
- [docs/DECISOES.md](docs/DECISOES.md): interview-style Q&A about the design
- [docs/LIMITACOES.md](docs/LIMITACOES.md): what was left out and how it would be done

## License

[MIT](LICENSE)
