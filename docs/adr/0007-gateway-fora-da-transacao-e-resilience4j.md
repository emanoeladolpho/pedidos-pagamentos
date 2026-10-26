# ADR 0007 — Gateway chamado fora da transação, protegido por Resilience4j

**Status:** aceita

## Contexto

O gateway de pagamento é lento e instável comparado ao banco. Manter uma transação (e uma conexão do pool)
aberta durante a chamada esgota o pool sob carga; chamar sem proteção propaga a instabilidade para o consumidor.

## Decisão

- `ProcessPaymentService` usa **duas transações**: (1) cria o pagamento `PENDING` e publica `PaymentInitiated`;
  a chamada ao gateway acontece **sem transação**; (2) registra o resultado, publica o evento e marca a mensagem como
  processada.
- O adapter compõe `retry(circuitBreaker(timeLimiter(chamada)))` com Resilience4j:
  - **timeout** (2s): a chamada roda em uma thread virtual e é abandonada no limite;
  - **retry** (3 tentativas, backoff exponencial) apenas para falhas técnicas e timeouts. Recusa é resultado de
    negócio, nunca é repetida;
  - **circuit breaker** (janela de 20 chamadas, abre com 50% de falhas): com o circuito aberto a chamada falha
    imediatamente, sem retry, preservando o provedor.
- O retry é seguro porque a cobrança usa o id do pagamento como chave de idempotência no provedor.
- Esgotadas as tentativas, `PaymentGatewayUnavailableException` sobe, o consumidor Kafka faz seus retries e a
  mensagem vai para a DLT; o pagamento continua `PENDING` e pode ser reprocessado.
- Configuração por instância (`resilience4j.*.instances.payment-gateway`) e métricas/health expostas no actuator.

## Alternativas consideradas

- **Anotações** (`@Retry`, `@CircuitBreaker`): menos código, mas com AOP a ordem entre os aspectos fica implícita,
  o `@TimeLimiter` exige retorno assíncrono e o comportamento é mais difícil de testar sem Spring.
- **Uma transação só**: mais simples, porém segura uma conexão durante segundos e pode deixar a cobrança feita
  com a transação revertida.

## Consequências

- Existe uma janela em que o pagamento está `PENDING` com a cobrança em andamento; reentregas tratam isso.
- Retries se multiplicam (tentativas do gateway × tentativas do consumidor); os valores foram escolhidos
  pensando nisso, e o circuit breaker limita o efeito durante uma indisponibilidade longa.
