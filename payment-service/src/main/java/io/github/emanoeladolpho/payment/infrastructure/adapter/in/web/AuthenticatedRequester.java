package io.github.emanoeladolpho.payment.infrastructure.adapter.in.web;

import io.github.emanoeladolpho.payment.application.port.in.Requester;
import io.github.emanoeladolpho.chassis.security.KeycloakJwtConverter;
import org.springframework.security.oauth2.jwt.Jwt;

/** Traduz o token autenticado para o conceito de solicitante da aplicação. */
final class AuthenticatedRequester {

    private AuthenticatedRequester() {
    }

    static Requester from(Jwt jwt) {
        return KeycloakJwtConverter.isAdmin(jwt) ? Requester.admin(jwt.getSubject()) : Requester.customer(jwt.getSubject());
    }
}
