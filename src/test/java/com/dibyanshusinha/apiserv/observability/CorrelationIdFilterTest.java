package com.dibyanshusinha.apiserv.observability;

import com.dibyanshusinha.apiserv.service.products.util.ProductConstants;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter(null);

    @Test
    void usesIncomingCorrelationIdWhenValid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationId.HEADER_NAME, "gateway-correlation-123");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE)).isEqualTo("gateway-correlation-123");
        assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo("gateway-correlation-123");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER_NAME))
                .isNotBlank()
                .isEqualTo(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE));
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationId.HEADER_NAME, "bad correlation id with spaces");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER_NAME))
                .isNotBlank()
                .isNotEqualTo("bad correlation id with spaces");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void usesTraceIdFromTracerWhenPresent() throws ServletException, IOException {
        io.micrometer.tracing.Tracer mockTracer = org.mockito.Mockito.mock(io.micrometer.tracing.Tracer.class);
        io.micrometer.tracing.Span mockSpan = org.mockito.Mockito.mock(io.micrometer.tracing.Span.class);
        io.micrometer.tracing.TraceContext mockContext = org.mockito.Mockito.mock(io.micrometer.tracing.TraceContext.class);

        org.mockito.Mockito.when(mockTracer.currentSpan()).thenReturn(mockSpan);
        org.mockito.Mockito.when(mockSpan.context()).thenReturn(mockContext);
        org.mockito.Mockito.when(mockContext.traceId()).thenReturn("mock-trace-id-456");

        CorrelationIdFilter tracerFilter = new CorrelationIdFilter(mockTracer);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();

        tracerFilter.doFilter(request, response, new MockFilterChain());

        assertThat(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE)).isEqualTo("mock-trace-id-456");
        assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo("mock-trace-id-456");
    }

    @Test
    void fallsBackToHeaderWhenTracerHasNoCurrentSpan() throws ServletException, IOException {
        io.micrometer.tracing.Tracer mockTracer = org.mockito.Mockito.mock(io.micrometer.tracing.Tracer.class);
        org.mockito.Mockito.when(mockTracer.currentSpan()).thenReturn(null);

        CorrelationIdFilter tracerFilter = new CorrelationIdFilter(mockTracer);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationId.HEADER_NAME, "fallback-correlation-789");

        tracerFilter.doFilter(request, response, new MockFilterChain());

        // No active span → falls back to incoming header
        assertThat(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE)).isEqualTo("fallback-correlation-789");
        assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo("fallback-correlation-789");
    }

    @Test
    void fallsBackToHeaderWhenSpanContextIsNull() throws ServletException, IOException {
        io.micrometer.tracing.Tracer mockTracer = org.mockito.Mockito.mock(io.micrometer.tracing.Tracer.class);
        io.micrometer.tracing.Span mockSpan = org.mockito.Mockito.mock(io.micrometer.tracing.Span.class);

        org.mockito.Mockito.when(mockTracer.currentSpan()).thenReturn(mockSpan);
        org.mockito.Mockito.when(mockSpan.context()).thenReturn(null);

        CorrelationIdFilter tracerFilter = new CorrelationIdFilter(mockTracer);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationId.HEADER_NAME, "fallback-no-ctx-111");

        tracerFilter.doFilter(request, response, new MockFilterChain());

        // Span with null context → falls back to incoming header
        assertThat(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE)).isEqualTo("fallback-no-ctx-111");
        assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo("fallback-no-ctx-111");
    }

    @Test
    void fallsBackToHeaderWhenTracerReturnsBlankTraceId() throws Exception {
        // Covers the `correlationId.isBlank()` branch:
        // tracer is present, span and context exist, but traceId() returns blank
        io.micrometer.tracing.Tracer mockTracer = org.mockito.Mockito.mock(io.micrometer.tracing.Tracer.class);
        io.micrometer.tracing.Span mockSpan = org.mockito.Mockito.mock(io.micrometer.tracing.Span.class);
        io.micrometer.tracing.TraceContext mockContext = org.mockito.Mockito.mock(io.micrometer.tracing.TraceContext.class);

        org.mockito.Mockito.when(mockTracer.currentSpan()).thenReturn(mockSpan);
        org.mockito.Mockito.when(mockSpan.context()).thenReturn(mockContext);
        org.mockito.Mockito.when(mockContext.traceId()).thenReturn("   "); // blank string

        CorrelationIdFilter tracerFilter = new CorrelationIdFilter(mockTracer);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", ProductConstants.API_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationId.HEADER_NAME, "fallback-blank-trace-222");

        tracerFilter.doFilter(request, response, new MockFilterChain());

        // Blank traceId → falls back to incoming header
        assertThat(request.getAttribute(CorrelationId.REQUEST_ATTRIBUTE)).isEqualTo("fallback-blank-trace-222");
        assertThat(response.getHeader(CorrelationId.HEADER_NAME)).isEqualTo("fallback-blank-trace-222");
    }
}
