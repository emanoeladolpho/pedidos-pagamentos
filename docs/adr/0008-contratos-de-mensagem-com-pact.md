# ADR 0008 — Envelope versionado e contratos de mensagem com Pact

**Status:** aceita

## Contexto

Os serviços evoluem separadamente. Renomear um campo de evento no produtor quebra o consumidor em produção, e
testes de integração de cada serviço isolado não detectam isso.

## Decisão

- Todo evento usa o envelope `{eventId, eventType, eventVersion, occurredAt, data}`. Os DTOs públicos
  (`OrderCreatedV1`, `PaymentApprovedV1`...) ficam no adapter de mensageria, separados dos eventos de domínio.
- Consumidores são *tolerant readers*: leem só os campos de que precisam e ignoram o resto.
- **Pact** nos dois sentidos:
  - o consumidor declara o mínimo que lê (com *matchers* de tipo/formato) e gera o contrato em `contracts/pacts`;
  - o provedor verifica o contrato com as mensagens produzidas **pelo mesmo mapper usado em produção**.
- Os contratos são versionados no repositório; o CI falha se o build regenerar contratos diferentes dos versionados.

## Alternativas consideradas

- **Schema Registry com Avro/Protobuf**: validação de compatibilidade no broker e payload menor, ao custo de mais
  infraestrutura e de um formato binário que dificulta a depuração. Faz sentido com muitos times e tópicos.
- **Pact Broker**: necessário quando os serviços estão em repositórios diferentes; aqui é um monorepo.

## Consequências

- Quebrar um contrato falha o build do provedor antes do deploy.
- Mudanças incompatíveis exigem nova versão do evento (`V2`) e convivência temporária das duas.
