package io.github.emanoeladolpho.chassis.web;

import io.github.emanoeladolpho.chassis.correlation.CorrelationId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseExceptionHandlerTest {

    record Body(@NotBlank String name) {
    }

    @RestController
    static class TestController {

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Body body) {
        }

        @PostMapping("/mixed/{value}")
        void mixed(@PathVariable @Min(10) int value, @Valid @RequestBody Body body) {
        }

        @GetMapping("/params/{value}")
        void params(@PathVariable @Min(10) int value) {
        }

        @GetMapping("/header")
        void header(@RequestHeader("Idempotency-Key") String key) {
        }

        @GetMapping("/fail/{kind}")
        void fail(@PathVariable String kind) {
            switch (kind) {
                case "illegal" -> throw new IllegalArgumentException("Valor inválido");
                case "lock" -> throw new OptimisticLockingFailureException("lock");
                case "integrity" -> throw new DataIntegrityViolationException("dup");
                case "denied" -> throw new AccessDeniedException("no");
                default -> throw new IllegalStateException("boom");
            }
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new BaseExceptionHandler())
            .build();

    @AfterEach
    void clear() {
        CorrelationId.clear();
    }

    @Test
    void invalidBodyShouldReturn400WithFieldErrors() throws Exception {
        CorrelationId.set("corr-web");

        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.correlationId").value("corr-web"));
    }

    @Test
    void invalidParameterShouldReturn400() throws Exception {
        mvc.perform(get("/params/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("value"));
    }

    @Test
    void bodyErrorsShouldBeListedAlsoWhenTheMethodHasParameterConstraints() throws Exception {
        mvc.perform(post("/mixed/20").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void missingHeaderShouldReturn400NamingTheHeader() throws Exception {
        mvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.detail").value("O header 'Idempotency-Key' é obrigatório"));
    }

    @Test
    void errorsHandledByTheFrameworkShouldAlsoCarryCodeAndCorrelationId() throws Exception {
        CorrelationId.set("corr-fw");

        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{json quebrado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.correlationId").value("corr-fw"));
        mvc.perform(get("/validated"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void exceptionsShouldBeMappedToStableCodes() throws Exception {
        mvc.perform(get("/fail/illegal")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.detail").value("Valor inválido"));
        mvc.perform(get("/fail/lock")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mvc.perform(get("/fail/integrity")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/fail/denied")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/fail/other")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado"));
    }
}
