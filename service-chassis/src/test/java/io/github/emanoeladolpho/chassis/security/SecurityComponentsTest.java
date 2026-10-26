package io.github.emanoeladolpho.chassis.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityComponentsTest {

    /** Mesmo builder usado pelo Spring Boot: registra o mixin que achata as propriedades do ProblemDetail. */
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private static Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void converterShouldMapRealmRolesToAuthoritiesAndUseSubjectAsName() {
        Jwt token = jwt(Map.of("realm_access", Map.of("roles", List.of("USER", "ADMIN"))));

        var authentication = new KeycloakJwtConverter().convert(token);

        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
        assertThat(KeycloakJwtConverter.isAdmin(token)).isTrue();
    }

    @Test
    void converterShouldReturnNoAuthoritiesWhenRolesAreMissingOrMalformed() {
        assertThat(KeycloakJwtConverter.authorities(jwt(Map.of("scope", "openid")))).isEmpty();
        assertThat(KeycloakJwtConverter.authorities(jwt(Map.of("realm_access", Map.of("roles", "ADMIN"))))).isEmpty();
        assertThat(KeycloakJwtConverter.isAdmin(jwt(Map.of("realm_access", Map.of("roles", List.of("USER"))))))
                .isFalse();
    }

    @Test
    void securityHandlerShouldWriteProblemDetailsFor401And403() throws Exception {
        var handler = new ProblemDetailsSecurityHandler(objectMapper);

        var unauthorized = new MockHttpServletResponse();
        handler.commence(new MockHttpServletRequest(), unauthorized, new BadCredentialsException("x"));
        var forbidden = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), forbidden, new AccessDeniedException("x"));

        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getContentType()).isEqualTo("application/problem+json");
        JsonNode body401 = objectMapper.readTree(unauthorized.getContentAsByteArray());
        assertThat(body401.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.getContentAsByteArray()).get("code").asText())
                .isEqualTo("ACCESS_DENIED");
    }
}
