# ADR 0009 — Keycloak como provedor de identidade e JWT validado localmente

**Status:** aceita

## Contexto

Os serviços precisam autenticar clientes e distinguir clientes de operadores, sem implementar cadastro, senha,
MFA ou emissão de tokens.

## Decisão

- **Keycloak** (OAuth2/OIDC) com o realm `pedidos` importado na subida, roles de realm `USER` e `ADMIN`.
  Senhas dos usuários de demonstração vêm de variáveis de ambiente (placeholders no JSON do realm).
- Os serviços são *resource servers* stateless: validam assinatura (RS256), expiração e emissor do JWT localmente,
  com as chaves públicas obtidas do endpoint JWKS (cacheadas).
- As roles vêm de `realm_access.roles` e viram `ROLE_USER`/`ROLE_ADMIN`; o `sub` do token é o id do cliente.
- Regras: qualquer rota de negócio exige `USER` ou `ADMIN`; estorno manual exige `ADMIN`; um cliente só enxerga
  os próprios pedidos e pagamentos. Recursos de outro cliente respondem `404` (não `403`), para não revelar
  que existem.
- O endereço de onde as chaves são baixadas (rede interna, `keycloak:8080`) é configurado separado do emissor
  esperado (endereço público, `localhost:8080`), como acontece atrás de proxies.
- `401` e `403` também respondem em Problem Details.

## Alternativas consideradas

- **Introspecção do token** a cada requisição: revogação imediata, mas cria dependência síncrona do Keycloak em
  toda chamada.
- **JWT próprio com HMAC** (como num monólito): simples, mas cada serviço precisaria do segredo e da gestão de
  usuários.

## Consequências

- Um token revogado continua válido até expirar (15 minutos).
- O script de demonstração usa o *password grant* por praticidade; um front-end real usaria Authorization Code +
  PKCE (ver LIMITACOES.md).
