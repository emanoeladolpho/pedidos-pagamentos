# ADR 0010 — Service chassis compartilhado e correlation id próprio

**Status:** aceita

## Contexto

Outbox, inbox, tratamento de erros do Kafka, formato de erro HTTP, validação de JWT e correlation id são iguais nos
dois serviços e não pertencem a nenhum domínio. Copiar esse código gera divergência; uma biblioteca "utils" sem
fronteira vira depósito de domínio compartilhado.

## Decisão

- Módulo **`service-chassis`** (padrão *Microservice Chassis*) publicado como auto-configuração do Spring Boot,
  dividida em duas: mensageria (outbox, inbox, DLT, interceptor do Kafka) e web (filtro de correlation id,
  Problem Details, JWT). A parte web também é carregada nos testes `@WebMvcTest`.
- O chassis **não contém conceitos de domínio**: só infraestrutura. Cada serviço mantém seus próprios value objects,
  mesmo quando parecidos (`Money`), para não acoplar os modelos.
- **Correlation id**: aceito no header `X-Correlation-Id` (ou gerado), sanitizado, colocado no MDC, devolvido na
  resposta, gravado na linha do outbox, enviado como header Kafka e restaurado no MDC do consumidor. Assim, um único
  id liga a requisição HTTP aos logs dos dois serviços.
- Logs em JSON (formato ECS, structured logging nativo do Spring Boot) nos containers; em texto localmente.
- Métricas com Micrometer/Prometheus: HTTP com histograma, eventos publicados por tipo (`outbox_events_published`),
  fila do outbox, consumidores Kafka, Resilience4j, JVM e pool de conexões; dashboard provisionado no Grafana.
- Health checks de *liveness* e *readiness* usados pelo Docker.

## Alternativas consideradas

- **OpenTelemetry com tracing distribuído** (Micrometer Tracing + OTLP + Tempo/Jaeger): mostra a árvore de spans
  e latências por etapa. É o próximo passo natural, mas exige coletor e backend; o correlation id resolve a
  rastreabilidade de logs com muito menos infraestrutura (ver LIMITACOES.md).
- **Copiar o código nos dois serviços**: zero acoplamento de build, mas divergência garantida com o tempo.

## Consequências

- Uma mudança no chassis afeta os dois serviços (versionados juntos neste monorepo).
- O chassis tem seus próprios testes (unitários e de integração com PostgreSQL e Kafka) e cobertura mínima.
