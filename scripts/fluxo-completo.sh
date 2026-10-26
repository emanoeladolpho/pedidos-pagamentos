#!/usr/bin/env bash
# Executa o fluxo completo contra o ambiente do docker compose:
#   pedido → pagamento aprovado → PAID, repetição idempotente, cancelamento → estorno,
#   pagamento recusado → FAILED e regras de acesso (401/403/404).
# Requisitos: curl e python3. Uso: ./scripts/fluxo-completo.sh
set -euo pipefail
cd "$(dirname "$0")/.."

[[ -f .env ]] || { echo "Arquivo .env não encontrado. Rode: cp .env.example .env" >&2; exit 1; }
set -a; source .env; set +a

KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8080}}
ORDERS_URL=${ORDERS_URL:-http://localhost:8081}
PAYMENTS_URL=${PAYMENTS_URL:-http://localhost:8082}
CORRELATION_ID="fluxo-$(date +%s)"
HEADERS_FILE=$(mktemp)
trap 'rm -f "$HEADERS_FILE"' EXIT

step() { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[0;32m  ✔ %s\033[0m\n' "$*"; }
fail() { printf '\033[0;31m  ✘ %s\033[0m\n' "$*" >&2; exit 1; }

# Lê um campo de um JSON da entrada padrão: json status | json items.0.sku
json() {
  python3 -c '
import json, sys
value = json.load(sys.stdin)
for key in filter(None, sys.argv[1].split(".")):
    value = value[int(key)] if isinstance(value, list) else value.get(key)
print("" if value is None else value)' "${1:-}"
}
pretty() { python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), indent=2, ensure_ascii=False))'; }
uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }

token() {
  curl -sf "$KEYCLOAK_URL/realms/pedidos/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=pedidos-api -d username="$1" --data-urlencode password="$2" | json access_token
}

# curl que devolve "<status>\n<corpo>"
call() { curl -s -w '\n%{http_code}' "$@"; }
status_of() { tail -n1 <<<"$1"; }
body_of() { sed '$d' <<<"$1"; }

place_order() { # $1 = token, $2 = idempotency key, $3 = itens JSON
  call -X POST "$ORDERS_URL/orders" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $2" -H "X-Correlation-Id: $CORRELATION_ID" -D "$HEADERS_FILE" \
    -d "{\"currency\":\"BRL\",\"items\":[$3]}"
}

wait_order_status() { # $1 = token, $2 = id, $3 = status esperado (regex)
  local current=""
  for _ in $(seq 1 60); do
    current=$(curl -sf "$ORDERS_URL/orders/$2" -H "Authorization: Bearer $1" | json status)
    [[ "$current" =~ ^($3)$ ]] && { echo "$current"; return 0; }
    sleep 0.5
  done
  echo "$current"; return 1
}

wait_payment_status() { # $1 = token, $2 = orderId, $3 = status esperado
  local current=""
  for _ in $(seq 1 60); do
    current=$(curl -s "$PAYMENTS_URL/payments?orderId=$2" -H "Authorization: Bearer $1" | json status || true)
    [[ "$current" == "$3" ]] && return 0
    sleep 0.5
  done
  return 1
}

step "Aguardando os serviços ficarem prontos"
for url in "$ORDERS_URL" "$PAYMENTS_URL"; do
  for _ in $(seq 1 90); do curl -sf "$url/actuator/health/readiness" >/dev/null && break; sleep 2; done
  curl -sf "$url/actuator/health/readiness" >/dev/null || fail "$url não ficou pronto"
done
ok "order-service e payment-service prontos"

step "Obtendo tokens no Keycloak (usuários 'cliente' e 'admin')"
CLIENT_TOKEN=$(token cliente "$KC_DEMO_USER_PASSWORD") || fail "login do cliente falhou"
ADMIN_TOKEN=$(token admin "$KC_DEMO_ADMIN_PASSWORD") || fail "login do admin falhou"
ok "tokens obtidos"

ITEMS='{"sku":"TECLADO-MEC-01","productName":"Teclado mecânico","unitPrice":349.90,"quantity":1},
       {"sku":"MOUSE-01","productName":"Mouse sem fio","unitPrice":120.00,"quantity":2}'

step "1. Criando pedido (Idempotency-Key + X-Correlation-Id: $CORRELATION_ID)"
# O gateway simulado pode recusar aleatoriamente (GATEWAY_DECLINE_RATE); nesse caso tenta com outro pedido.
for attempt in 1 2 3; do
  KEY=$(uuid)
  RESPONSE=$(place_order "$CLIENT_TOKEN" "$KEY" "$ITEMS")
  [[ $(status_of "$RESPONSE") == 201 ]] || fail "esperava 201, recebeu $(status_of "$RESPONSE"): $(body_of "$RESPONSE")"
  ORDER_ID=$(body_of "$RESPONSE" | json id)
  body_of "$RESPONSE" | pretty
  FINAL=$(wait_order_status "$CLIENT_TOKEN" "$ORDER_ID" "PAID|FAILED") || fail "pedido não saiu de $FINAL"
  [[ "$FINAL" == PAID ]] && break
  echo "  (recusa aleatória do gateway simulado, tentativa $attempt; criando outro pedido)"
done
[[ "$FINAL" == PAID ]] || fail "pedido não foi pago"
ok "pedido $ORDER_ID → PAID"

step "2. Repetindo a mesma requisição (mesma Idempotency-Key)"
REPLAY=$(place_order "$CLIENT_TOKEN" "$KEY" "$ITEMS")
[[ $(body_of "$REPLAY" | json id) == "$ORDER_ID" ]] || fail "replay criou outro pedido"
grep -qi 'Idempotent-Replayed: true' "$HEADERS_FILE" || fail "header Idempotent-Replayed ausente"
ok "mesmo pedido devolvido, header Idempotent-Replayed: true"
CONFLICT=$(place_order "$CLIENT_TOKEN" "$KEY" '{"sku":"OUTRO-01","productName":"Outro","unitPrice":1.00,"quantity":1}')
[[ $(status_of "$CONFLICT") == 422 ]] || fail "esperava 422 para chave reutilizada com outro corpo"
ok "mesma chave com outro corpo → 422 $(body_of "$CONFLICT" | json code)"

step "3. Pagamento do pedido"
curl -sf "$PAYMENTS_URL/payments?orderId=$ORDER_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | pretty

step "4. Cancelando o pedido pago → estorno automático"
call -X POST "$ORDERS_URL/orders/$ORDER_ID/cancel" -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' -d '{"reason":"Desisti da compra"}' >/dev/null
wait_payment_status "$CLIENT_TOKEN" "$ORDER_ID" REFUNDED || fail "pagamento não foi estornado"
ok "pedido CANCELLED e pagamento REFUNDED"

step "5. Pagamento recusado (total com centavos .13)"
RESPONSE=$(place_order "$CLIENT_TOKEN" "$(uuid)" '{"sku":"MONITOR-01","productName":"Monitor","unitPrice":899.13,"quantity":1}')
DECLINED_ID=$(body_of "$RESPONSE" | json id)
wait_order_status "$CLIENT_TOKEN" "$DECLINED_ID" FAILED >/dev/null || fail "pedido recusado não foi para FAILED"
ok "pedido $DECLINED_ID → FAILED ($(curl -sf "$ORDERS_URL/orders/$DECLINED_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | json failureReason))"

step "6. Regras de acesso"
[[ $(status_of "$(call "$ORDERS_URL/orders")") == 401 ]] || fail "sem token deveria ser 401"
ok "sem token → 401"
PAYMENT_ID=$(curl -sf "$PAYMENTS_URL/payments?orderId=$ORDER_ID" -H "Authorization: Bearer $CLIENT_TOKEN" | json id)
[[ $(status_of "$(call -X POST "$PAYMENTS_URL/payments/$PAYMENT_ID/refund" -H "Authorization: Bearer $CLIENT_TOKEN" \
  -H 'Content-Type: application/json' -d '{"reason":"x"}')") == 403 ]] || fail "estorno por USER deveria ser 403"
ok "estorno manual por USER → 403"
TOTAL=$(curl -sf "$ORDERS_URL/orders?size=1" -H "Authorization: Bearer $ADMIN_TOKEN" | json totalElements)
ok "ADMIN enxerga todos os pedidos ($TOTAL no total)"

step "Concluído"
echo "  Rastreie a requisição nos dois serviços pelo correlation id:"
echo "    docker compose logs order-service payment-service | grep $CORRELATION_ID"
echo "  Métricas: http://localhost:3000 (Grafana) · Swagger: $ORDERS_URL/swagger-ui.html"
