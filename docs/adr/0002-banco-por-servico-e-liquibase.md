# ADR 0002 — Um banco PostgreSQL por serviço, migrations com Liquibase

**Status:** aceita

## Contexto

Serviços que compartilham tabelas ficam acoplados pelo esquema: uma migration de um quebra o outro, e é tentador
"resolver" consistência com joins e transações entre domínios.

## Decisão

- Cada serviço tem seu próprio PostgreSQL (`orders` e `payments`); nenhum serviço lê o banco do outro.
- Os dados de que o payment-service precisa (valor, cliente) chegam no evento `OrderCreated`.
- Esquema versionado com Liquibase (YAML), aplicado na subida; Hibernate só valida (`ddl-auto: validate`).
- As tabelas transversais (outbox e inbox) vêm de um changelog do `service-chassis`, incluído pelo master de cada
  serviço.
- Restrições importantes também no banco: `CHECK` de status, unicidade de `(customer_id, idempotency_key)`,
  unicidade de `payments.order_id`, chave primária `(event_id, consumer)` no inbox.

## Alternativas consideradas

- **Um banco com schemas separados**: mais barato, mas a fronteira vira convenção.
- **Flyway**: igualmente adequado; o Liquibase foi escolhido pelo formato declarativo e pelo `include` de changelogs
  de bibliotecas.

## Consequências

- Não há transação entre serviços: a consistência é eventual (ver ADR 0003 e 0004).
- Consultas que cruzam domínios precisariam de composição na API ou de um read model (fora do escopo).
- As constraints do banco são a última linha de defesa contra corridas (ex.: duas instâncias processando o mesmo
  pedido).
