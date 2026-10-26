# Decisões: roteiro de perguntas e respostas

Perguntas que costumam aparecer numa entrevista sobre este projeto, com respostas curtas e o "porquê" de cada
escolha. Os detalhes de cada decisão estão nos [ADRs](adr/).

---

## Arquitetura

**1. Por que arquitetura hexagonal? Não é exagero para dois serviços pequenos?**
O valor está em separar o que muda por motivos diferentes. As regras de pedido e pagamento não mudam quando se
troca o Kafka, o banco ou o gateway. Com portas e adapters, o domínio e os casos de uso são testados em milissegundos,
sem Spring, e as integrações são testadas isoladamente com containers reais. O custo é o mapeamento
(entidade JPA × agregado, DTO × modelo), que o MapStruct reduz. Em um CRUD sem regra eu não usaria.

**2. Como você garante que a arquitetura é respeitada e não fica só no diagrama?**
Com ArchUnit no build: domínio e aplicação não podem importar Spring, JPA, Jackson, Kafka nem MapStruct; nenhuma
camada interna pode depender da infraestrutura; adapters não dependem uns dos outros; todo caso de uso implementa
uma porta de entrada; portas de saída são interfaces. Um import errado falha o `mvn verify`.

**3. Os casos de uso não têm `@Service` nem `@Transactional`. Como funciona a transação?**
A transação é uma porta de saída, `UnitOfWork`, implementada na infraestrutura com `TransactionTemplate`. Isso deixa
o limite transacional explícito no caso de uso, o que importa no pagamento: são duas transações com a chamada ao
gateway no meio. Os casos de uso são instanciados no composition root (`infrastructure.config`).

**4. O que é DDD tático aqui?**
Agregados (`Order`, `Payment`) que protegem invariantes e só mudam por métodos de negócio (`cancel`, `markPaid`,
`refund`); value objects imutáveis como `record` (`Money`, `Sku`, `Quantity`) que validam a si mesmos; eventos de
domínio registrados pelo agregado e coletados pela aplicação (`pullDomainEvents`) para publicação via outbox.
Repositórios por agregado. Não usei DDD estratégico formal porque são dois *bounded contexts* óbvios.

**5. O que é o `service-chassis`? Não vira um "common" que acopla tudo?**
É o padrão *Microservice Chassis*: infraestrutura transversal (outbox, inbox, DLT, correlation id, Problem Details,
JWT), como auto-configuração do Spring Boot. A regra é não ter nenhum conceito de domínio ali. Por isso `Money`
existe duplicado nos dois serviços: modelos de domínio não são compartilhados.

---

## Consistência e mensageria

**6. Por que não usar uma transação distribuída?**
O Kafka não participa de 2PC com o banco, e 2PC acopla a disponibilidade de todos os participantes. Usei uma saga:
transações locais em cada serviço, ligadas por eventos, com compensações (estorno quando um pedido pago é cancelado).

**7. Coreografia ou orquestração?**
Coreografia: cada serviço reage aos eventos do outro. Com dois participantes e quatro eventos, um orquestrador
seria mais um componente sem ganho. Se entrassem estoque e entrega, eu reavaliaria, porque o fluxo coreografado
fica difícil de enxergar.

**8. O que é o Transactional Outbox e qual problema resolve?**
Resolve o *dual write*: gravar no banco e publicar no Kafka não é atômico. Se o commit acontece e a publicação falha,
o pedido nunca é cobrado; se publica e o commit falha, cobra-se um pedido inexistente. Com outbox o evento é gravado
numa tabela na mesma transação do pedido, e um relay publica depois. O evento existe se e somente se a mudança de
estado existe.

**9. Como o relay funciona com várias instâncias?**
Cada instância busca um lote com `SELECT ... FOR UPDATE SKIP LOCKED`: as linhas travadas por uma instância são
puladas pelas outras, sem fila de espera nem processamento duplicado em condições normais. Publica aguardando o
*ack* do Kafka (`acks=all`, producer idempotente), marca como publicado e para no primeiro erro para preservar a
ordem.

**10. E se o relay cair depois de publicar e antes de marcar como publicado?**
A mensagem é publicada de novo: a garantia é *at-least-once*. Por isso todo consumidor é idempotente.

**11. Como os consumidores são idempotentes?**
Com uma tabela de inbox `processed_events (event_id, consumer)`. Na mesma transação da mudança de estado, o caso de
uso verifica se o evento já foi processado e o registra. O registro é um `INSERT` (forcei `persist` em vez de `merge`
com `Persistable`); se duas instâncias processarem ao mesmo tempo, a chave primária duplicada aborta uma delas.

**12. E a ordem dos eventos?**
A chave da mensagem é o id do pedido, então todos os eventos de um pedido vão para a mesma partição e são consumidos
em ordem. `OrderCancelled` nunca é processado antes do `OrderCreated` do mesmo pedido. A ordem global não é garantida
nem necessária.

**13. O que acontece quando uma mensagem não pode ser processada?**
`DefaultErrorHandler` com backoff exponencial; esgotadas as tentativas, a mensagem original vai para `<tópico>-dlt`
com headers da exceção. Mensagens malformadas vão direto, sem retry. Eventos de tipo desconhecido são ignorados
(*tolerant reader*), porque um produtor mais novo pode publicar tipos que este consumidor ainda não conhece.

**14. Por que retry bloqueante e não tópicos de retry (`@RetryableTopic`)?**
Tópicos de retry não bloqueiam a partição, mas tiram a mensagem da ordem: o `OrderCancelled` poderia ser processado
antes do `OrderCreated` que está em retry. Aqui a ordem por pedido vale mais que a vazão durante falhas.

**15. Encontrou algum bug de mensageria durante o desenvolvimento?**
Sim, no teste ponta a ponta. O consumidor que subia primeiro criava o tópico implicitamente com 1 partição; o outro
serviço aumentava para 3, e o consumidor só descobria as novas partições no próximo refresh de metadados (5 minutos).
Eventos ficavam parados. A correção: cada serviço declara todos os tópicos que usa com a mesma especificação,
`allow.auto.create.topics=false` no consumidor, auto-criação desligada no broker e `metadata.max.age.ms` menor.

---

## API e idempotência

**16. Como funciona a `Idempotency-Key`?**
É obrigatória no `POST /orders`. Guardo a chave por cliente, com o hash SHA-256 de uma forma canônica do pedido, na
mesma transação. Mesma chave e mesmo corpo: devolvo o pedido original com `Idempotent-Replayed: true`. Mesma chave
com outro corpo: `422`. Duas requisições simultâneas: a constraint única deixa só uma passar; a outra, repetida,
cai no replay.

**17. Por que um cliente recebe 404 e não 403 ao acessar o pedido de outro?**
Para não revelar a existência do recurso (enumeração de ids). Para o cliente, o pedido de outra pessoa não existe.

**18. Como os erros são padronizados?**
Problem Details (RFC 9457) com campos extras: `code` estável para o cliente tratar, `timestamp` e `correlationId`.
Inclusive para os erros que o próprio Spring trata (404 de rota, 405, JSON inválido) e para 401/403 do Spring
Security.

**19. Validação: onde fica cada regra?**
Formato na borda (Bean Validation nos DTOs: SKU, moeda, quantidade, tamanho); regra de negócio no domínio (SKU
repetido, mistura de moedas, limite de itens, transições de estado). O domínio valida tudo de novo porque não pode
confiar em quem o chama.

---

## Pagamento e resiliência

**20. Por que o pagamento usa duas transações?**
Para não segurar uma conexão do pool durante a chamada ao gateway, que pode levar segundos. A primeira transação
cria o pagamento `PENDING` e publica `PaymentInitiated`; a chamada acontece fora de transação; a segunda registra o
resultado e marca o evento como processado. Se o serviço cair no meio, a reentrega encontra o `PENDING` e cobra de
novo com a mesma chave de idempotência.

**21. Cobrar duas vezes não é um risco com esse retry?**
Seria, se o provedor não fosse idempotente. A cobrança envia o id do pagamento como chave de idempotência, como
fazem os provedores reais (Stripe, Adyen); repetir a chamada devolve o resultado original. O simulador reproduz
esse comportamento e há teste para isso.

**22. Como os padrões de resiliência estão combinados?**
`retry(circuitBreaker(timeLimiter(chamada)))`. O timeout (2s) limita cada tentativa; o retry (3 tentativas, backoff
exponencial) só repete falhas técnicas; recusa é resultado de negócio. Cada tentativa conta para o circuit breaker;
com o circuito aberto a chamada falha na hora, sem retry. Usei a API programática em vez das anotações para deixar
a ordem explícita e testar sem Spring.

**23. O que acontece se o gateway ficar fora do ar por muito tempo?**
O circuito abre, as mensagens esgotam as tentativas do consumidor e vão para a DLT; os pagamentos ficam `PENDING` e
os pedidos em `PAYMENT_PENDING`. Quando o provedor volta, as mensagens da DLT são reenviadas e, por causa da
idempotência, o reprocessamento é seguro. Validei isso no ambiente do compose com o valor que simula falha.

**24. Por que thread virtual no TimeLimiter?**
O TimeLimiter precisa executar a chamada em outra thread para poder abandoná-la. Com threads virtuais (Java 21) uma
chamada bloqueada não ocupa uma thread de plataforma de um pool fixo.

---

## Segurança

**25. Como funciona a autenticação?**
Keycloak emite JWT assinado (RS256). Os serviços são resource servers stateless: validam assinatura, expiração e
emissor localmente, com as chaves públicas baixadas do endpoint JWKS e cacheadas. As roles vêm de
`realm_access.roles`; o `sub` é o id do cliente.

**26. Por que o emissor e o endereço das chaves são configurados separadamente?**
O cliente obtém o token por `localhost:8080` (então `iss` é esse endereço), mas dentro da rede do Docker o serviço
alcança o Keycloak por `keycloak:8080`. É o mesmo cenário de serviços atrás de um proxy ou ingress.

**27. Qual a desvantagem de validar o JWT localmente?**
Revogação: um token comprometido vale até expirar. Mitigação: vida curta (15 minutos) e refresh token. Introspecção
a cada requisição resolveria, ao custo de uma chamada síncrona ao Keycloak por requisição.

---

## Testes

**28. Qual a estratégia de testes?**
Pirâmide: muitos testes de domínio e de casos de uso (rápidos, sem framework); ArchUnit; testes web com MockMvc e JWT
simulado; integração com Testcontainers (PostgreSQL e Kafka reais: outbox, consumidores, DLT); contratos Pact; e um
teste ponta a ponta com os dois serviços reais, dois bancos, Kafka e tokens assinados por um JWKS de teste.
JaCoCo exige 80% de linhas por módulo (unitários + integração somados).

**29. Para que Pact, se já existe o teste ponta a ponta?**
O ponta a ponta valida o fluxo hoje, com os dois serviços na mesma versão. O Pact protege a evolução separada: cada
consumidor declara o mínimo que lê, e o build do provedor verifica que as mensagens reais (geradas pelo mesmo mapper
de produção) satisfazem o contrato. Uma quebra aparece no build de quem a causou, antes do deploy.

**30. Como o teste ponta a ponta autentica sem Keycloak?**
Um servidor HTTP do próprio JDK publica um JWKS com uma chave RSA gerada no teste, e o teste assina tokens com o
mesmo formato do Keycloak. Os serviços validam os tokens exatamente como em produção. O Keycloak real é exercitado
no `docker compose` pelo `scripts/fluxo-completo.sh`.

---

## Observabilidade

**31. Como você rastrearia um pedido com problema?**
Pelo correlation id: chega no header `X-Correlation-Id` (ou é gerado), vai para o MDC, é gravado na linha do outbox,
vira header da mensagem Kafka e é restaurado no MDC do consumidor no outro serviço. Os logs JSON dos dois serviços
podem ser filtrados pelo mesmo id. O teste ponta a ponta verifica que o id enviado ao order-service aparece nos
eventos produzidos pelo payment-service.

**32. Que métricas importam aqui?**
Eventos de negócio por tipo (pedidos criados, pagamentos aprovados/recusados), eventos pendentes no outbox (se
crescer, o Kafka está fora ou o relay travou), latência p95 e taxa de erro HTTP, sucesso/falha dos consumidores,
estado do circuit breaker e retries do gateway, heap e pool de conexões. Estão no dashboard do Grafana.

**33. Por que não OpenTelemetry?**
O correlation id resolve a rastreabilidade de logs com pouca infraestrutura. Tracing distribuído (Micrometer
Tracing + OTLP + Tempo/Jaeger) seria o próximo passo, para ver a latência de cada etapa. Está em LIMITACOES.md.

---

## Trade-offs e evolução

**34. O que você faria diferente em produção?**
Ver [LIMITACOES.md](LIMITACOES.md). Os principais: preço vindo de um catálogo, ferramenta de reprocessamento da DLT,
expurgo de outbox e inbox, Kafka com replicação, tracing distribuído, Authorization Code + PKCE e endpoints de
métricas fora da rede pública.

**35. Qual foi a decisão mais difícil?**
O limite transacional do pagamento. Uma transação só é mais simples, mas segura conexões e pode cobrar com a
transação revertida. Duas transações exigem pensar em todas as falhas intermediárias: reentrega com `PENDING`
existente, idempotência no provedor, evento marcado como processado só no final. Os testes dos casos de uso cobrem
cada uma dessas situações.
