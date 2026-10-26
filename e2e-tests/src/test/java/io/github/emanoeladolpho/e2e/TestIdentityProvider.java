package io.github.emanoeladolpho.e2e;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Faz o papel do Keycloak no teste: publica as chaves públicas (JWKS) por HTTP e emite tokens RS256
 * com o mesmo formato de claims do Keycloak (iss, sub, realm_access.roles). Os serviços validam os tokens
 * exatamente como em produção, baixando as chaves pela jwk-set-uri.
 */
final class TestIdentityProvider implements AutoCloseable {

    private final RSAKey key;
    private final HttpServer server;

    TestIdentityProvider() throws JOSEException, IOException {
        this.key = new RSAKeyGenerator(2048).keyID("e2e-key").generate();
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/certs", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwks);
            }
        });
        server.start();
    }

    String jwkSetUri() {
        return "http://localhost:" + server.getAddress().getPort() + "/certs";
    }

    String issuer() {
        return "http://localhost:" + server.getAddress().getPort() + "/realms/e2e";
    }

    String token(String subject, String... roles) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer())
                    .subject(subject)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                    .claim("realm_access", Map.of("roles", List.of(roles)))
                    .build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
