# ADR 0006 — `Idempotency-Key` na criação de pedidos

**Status:** aceita

## Contexto

Timeouts e retries do cliente (ou de um proxy) podem repetir `POST /orders`. Sem proteção, o cliente teria dois
pedidos e duas cobranças.

## Decisão

- O header `Idempotency-Key` é obrigatório no `POST /orders` (8 a 100 caracteres seguros, tipicamente um UUID).
- A chave é registrada por cliente (`customer_id + idempotency_key`, único no banco) na mesma transação do pedido,
  com o hash SHA-256 de uma representação canônica do pedido.
- Mesma chave e mesmo conteúdo → devolve o pedido original com `201` e header `Idempotent-Replayed: true`.
- Mesma chave e conteúdo diferente → `422 IDEMPOTENCY_KEY_REUSED`.
- Duas requisições simultâneas com a mesma chave: a constraint única faz uma delas falhar (`409`); repetida, ela
  cai no caminho de replay.

## Alternativas consideradas

- **Deduplicação pelo conteúdo** (mesmo carrinho em X minutos): adivinha a intenção do cliente e bloqueia compras
  legítimas repetidas.
- **Guardar a resposta HTTP serializada** (modelo da Stripe): devolve exatamente os mesmos bytes, mas duplica
  dados; aqui o estado atual do pedido é devolvido, o que é mais útil para o cliente.

## Consequências

- O cliente precisa gerar e reutilizar a chave nos retries; isso está documentado no OpenAPI.
- As chaves não expiram (ver LIMITACOES.md).
