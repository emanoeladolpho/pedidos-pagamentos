# ADR 0001 — Arquitetura hexagonal com DDD tático, verificada por ArchUnit

**Status:** aceita

## Contexto

Os dois serviços têm regras de negócio (transições de estado de pedido e pagamento, cálculo de total, idempotência)
que precisam ser testadas rapidamente e sem infraestrutura, e integrações que mudam por motivos diferentes das
regras (banco, Kafka, gateway de pagamento, HTTP). Arquiteturas em camadas "tradicionais" acabam com entidades JPA
como modelo de domínio e regras espalhadas em services anotados com `@Transactional`.

## Decisão

- Cada serviço tem três pacotes: `domain`, `application` e `infrastructure`.
- **Domínio** em Java puro: agregados (`Order`, `Payment`) que protegem suas invariantes, value objects como
  `record` (`Money`, `Sku`, `Quantity`...) e eventos de domínio registrados pelo agregado (`pullDomainEvents`).
- **Aplicação**: um caso de uso por classe, expostos por portas de entrada (interfaces); dependências externas
  por portas de saída (`OrderRepository`, `DomainEventPublisher`, `PaymentGateway`, `UnitOfWork`...).
  As classes de caso de uso não têm anotações de Spring; são instanciadas no *composition root*
  (`infrastructure.config`).
- A transação é uma porta (`UnitOfWork`) para que a aplicação decida os limites transacionais sem depender do Spring
  (necessário no pagamento, que faz duas transações separadas pela chamada ao gateway).
- **Infraestrutura**: adapters REST, Kafka, JPA (entidades separadas do domínio, mapeadas com MapStruct) e gateway.
- **ArchUnit** quebra o build se: domínio ou aplicação dependerem de Spring/JPA/Jackson; alguma camada interna
  depender da infraestrutura; adapters dependerem uns dos outros; casos de uso não implementarem uma porta;
  portas de saída não forem interfaces.

## Alternativas consideradas

- **Camadas com entidades JPA no domínio**: menos código de mapeamento, mas o modelo passa a ser guiado pelo
  banco (construtores vazios, setters, lazy loading) e os testes de regra precisam de contexto Spring.
- **Casos de uso anotados com `@Service`/`@Transactional`**: mais simples, mas acopla a aplicação ao framework e
  esconde onde a transação começa e termina.

## Consequências

- Testes de domínio e de casos de uso rodam em milissegundos, sem Spring.
- Há duplicação deliberada: entidades JPA × agregados, DTOs × modelo, e o mapeamento entre eles.
- Agregados são reconstruídos com `restore(...)` e usam `version` anulável (nulo = ainda não persistido) para que o
  adapter saiba quando fazer `persist` em vez de `merge`.
- As regras de dependência não dependem de disciplina: um import errado falha o build.
