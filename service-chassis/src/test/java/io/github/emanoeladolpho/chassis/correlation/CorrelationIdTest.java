package io.github.emanoeladolpho.chassis.correlation;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTest {

    @AfterEach
    void clear() {
        CorrelationId.clear();
    }

    @Test
    void sanitizeShouldKeepSafeValuesAndReplaceUnsafeOnes() {
        assertThat(CorrelationId.sanitize("abc-123_X.y")).isEqualTo("abc-123_X.y");
        assertThat(CorrelationId.sanitize(null)).hasSize(36);
        assertThat(CorrelationId.sanitize("quebra\nde linha")).isNotEqualTo("quebra\nde linha").hasSize(36);
        assertThat(CorrelationId.sanitize("x".repeat(65))).hasSize(36);
    }

    @Test
    void currentOrNewShouldReuseValueFromMdc() {
        assertThat(CorrelationId.current()).isEmpty();
        assertThat(CorrelationId.currentOrNew()).hasSize(36);

        CorrelationId.set("corr-1");

        assertThat(CorrelationId.currentOrNew()).isEqualTo("corr-1");
    }

    @Test
    void filterShouldPropagateIncomingHeaderToMdcAndResponseAndClearAfterwards() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "corr-http");
        var response = new MockHttpServletResponse();
        var seenInsideChain = new AtomicReference<String>();

        new CorrelationIdFilter().doFilter(request, response,
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                    @Override
                    protected void service(jakarta.servlet.http.HttpServletRequest req,
                                           jakarta.servlet.http.HttpServletResponse res) {
                        seenInsideChain.set(CorrelationId.current().orElse(null));
                    }
                }));

        assertThat(seenInsideChain.get()).isEqualTo("corr-http");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("corr-http");
        assertThat(CorrelationId.current()).isEmpty();
    }

    @Test
    void filterShouldGenerateIdWhenHeaderIsMissing() throws Exception {
        var response = new MockHttpServletResponse();

        new CorrelationIdFilter().doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER)).hasSize(36);
    }

    @Test
    void recordInterceptorShouldSetMdcFromHeaderAndClearAfterRecord() {
        var interceptor = new CorrelationIdRecordInterceptor();
        var record = new ConsumerRecord<Object, Object>("topic", 0, 0L, "key", "value");
        record.headers().add(CorrelationId.HEADER, "corr-kafka".getBytes(StandardCharsets.UTF_8));

        assertThat(interceptor.intercept(record, null)).isSameAs(record);
        assertThat(CorrelationId.current()).contains("corr-kafka");

        interceptor.afterRecord(record, null);
        assertThat(CorrelationId.current()).isEmpty();
    }

    @Test
    void recordInterceptorShouldGenerateIdWhenHeaderIsMissing() {
        new CorrelationIdRecordInterceptor().intercept(new ConsumerRecord<>("topic", 0, 0L, "key", "value"), null);

        assertThat(CorrelationId.current()).hasValueSatisfying(id -> assertThat(id).hasSize(36));
    }
}
