# Limitações conhecidas

O que ficou fora do escopo, de propósito ou por complexidade, e como seria resolvido em um sistema de produção.

## Domínio

- **Preço informado pelo cliente.** O pedido recebe `unitPrice` na requisição; não existe serviço de catálogo.
  Em produção, o order-service buscaria o preço (e o nome do produto) no catálogo ou receberia uma cotação assinada
  com validade, nunca confiando no valor enviado pelo cliente.
- **Sem estoque nem entrega.** A saga tem dois participantes. Com reserva de estoque, a compensação ficaria mais rica
  (liberar estoque em falha de pagamento), e a escolha entre coreografia e orquestração seria reavaliada (ADR 0003).
- **Uma moeda por pedido, sem conversão** e sem impostos, frete ou descontos.
- **Estorno sempre total.** Estorno parcial exigiria registrar valor estornado e saldo no `Payment`.

## Mensageria

- **Pedido cancelado enquanto o `OrderCreated` está na DLT, antes de existir pagamento.** O `OrderCancelled` não
  encontra pagamento e não faz nada; se o `OrderCreated` for reprocessado depois, o pedido cancelado seria cobrado
  (o order-service ignora o `PaymentApproved` para pedido cancelado, então o dinheiro ficaria retido).
  Correção: no `OrderCancelled` sem pagamento, registrar um marcador "pedido cancelado" no payment-service e
  recusar a cobrança ao reprocessar; ou fazer o order-service reagir a `PaymentApproved` de pedido cancelado
  solicitando o estorno.
- **Sem ferramenta de reprocessamento da DLT.** Hoje o reenvio é manual (ferramentas do Kafka). Em produção:
  um endpoint ou job administrativo que lista, inspeciona e reenvia mensagens da DLT, com auditoria. O
  reprocessamento é seguro por causa da idempotência.
- **Sem expurgo do outbox e do inbox.** As linhas publicadas (`outbox_events`) e processadas (`processed_events`)
  crescem indefinidamente. Solução: job periódico apagando por idade (ex.: 7 dias, maior que a retenção do tópico),
  ou particionamento da tabela por data.
- **Relay por polling.** Simples e portável, com latência de até um intervalo (500 ms). CDC com Debezium daria
  latência menor sem polling; o formato da tabela já é compatível (ADR 0004).
- **Kafka de um nó só** no compose (fator de replicação 1). Em produção: 3+ brokers, `min.insync.replicas=2`,
  `replication.factor=3`.
- **Sem Schema Registry.** Os contratos são JSON verificados com Pact (ADR 0008). Com muitos times, Avro ou Protobuf
  com regras de compatibilidade no registry seriam mais adequados.
- **Retry bloqueante.** Uma mensagem com problema atrasa a partição durante o backoff (segundos). Foi uma escolha
  para preservar a ordem por pedido (ADR 0005).

## Pagamento

- **Gateway simulado em memória.** A idempotência do simulador não sobrevive a reinícios; um provedor real a mantém.
  O adapter seria trocado por um cliente HTTP (RestClient) com o mesmo contrato e as mesmas políticas Resilience4j.
- **Sem conciliação.** Se o provedor cobrar e a resposta se perder em todas as tentativas, o pagamento fica `PENDING`
  até o reprocessamento da DLT. Em produção, um job consultaria o status no provedor (ou receberia webhooks) para
  pagamentos `PENDING` antigos.
- **Retries multiplicados.** Tentativas do gateway × tentativas do consumidor. Os valores padrão consideram isso, e o
  circuit breaker limita o efeito durante indisponibilidades longas.

## API

- **Chaves de idempotência sem expiração.** Em produção expirariam (ex.: 24 h, como na Stripe), com expurgo.
- **Sem rate limiting nem API gateway.** Seria feito no gateway/ingress (limites por cliente) e não nos serviços.
- **Listagem sem filtros** (status, período) e paginação por offset. Com volume alto, paginação por cursor.

## Segurança

- **Password grant no script de demonstração.** Prático para scripts, mas desaconselhado (removido no OAuth 2.1).
  Um front-end usaria Authorization Code + PKCE; integrações máquina a máquina, Client Credentials.
- **Keycloak em modo `start-dev`** com banco embutido. Em produção: `start` com PostgreSQL próprio, HTTPS e realm
  gerenciado como código (ex.: keycloak-config-cli ou Terraform).
- **Endpoints do actuator públicos** (`/actuator/prometheus`, health). Em produção ficariam numa porta de
  gerenciamento separada, acessível só pela rede interna.
- **Revogação de token** só por expiração (15 min), já que o JWT é validado localmente (ADR 0009).
- **Sem TLS entre os componentes** no compose, nem segredos em um cofre (Vault, AWS Secrets Manager); as senhas vêm
  de variáveis de ambiente.

## Observabilidade

- **Sem tracing distribuído.** O correlation id liga os logs dos dois serviços; Micrometer Tracing + OpenTelemetry
  (OTLP) + Tempo/Jaeger mostraria a latência de cada etapa (HTTP → outbox → Kafka → gateway).
- **Sem agregação de logs** (Loki, ELK). Os logs estão em JSON prontos para isso, mas o compose não inclui o
  agregador.
- **Sem alertas.** O dashboard existe; faltam regras de alerta (outbox crescendo, circuit breaker aberto, taxa de 5xx,
  mensagens na DLT).

## Testes e entrega

- **Ponta a ponta na mesma JVM.** O teste sobe os dois serviços via `SpringApplicationBuilder` (rápido e com cobertura),
  e não as imagens Docker. O fluxo com as imagens reais e o Keycloak é validado pelo `scripts/fluxo-completo.sh`.
- **Sem testes de carga** (Gatling, k6) nem de caos (derrubar o Kafka durante o fluxo).
- **CI não publica imagens** em registry nem faz deploy; apenas constrói e valida.
