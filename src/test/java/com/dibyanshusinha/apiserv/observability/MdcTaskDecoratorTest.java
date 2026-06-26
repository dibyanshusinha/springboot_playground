package com.dibyanshusinha.apiserv.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void decoratedRunnableInheritsCallerMdcContext() throws InterruptedException {
        MDC.put("correlationId", "test-correlation-123");
        MDC.put("traceId", "trace-abc");

        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<Map<String, String>> capturedContext = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> capturedContext.set(MDC.getCopyOfContextMap()));

        // Run in a new thread to verify context propagation
        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(capturedContext.get()).isNotNull();
        assertThat(capturedContext.get()).containsEntry("correlationId", "test-correlation-123");
        assertThat(capturedContext.get()).containsEntry("traceId", "trace-abc");
    }

    @Test
    void decoratedRunnableClearsMdcAfterExecution() throws InterruptedException {
        MDC.put("correlationId", "caller-id");

        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<Map<String, String>> mdcAfterRun = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            // No-op task
        });

        Thread thread = new Thread(() -> {
            MDC.put("thread-local-key", "should-be-cleared");
            decorated.run();
            mdcAfterRun.set(MDC.getCopyOfContextMap());
        });
        thread.start();
        thread.join();

        // MDC should be cleared after the decorated runnable completes
        assertThat(mdcAfterRun.get()).isNullOrEmpty();
    }

    @Test
    void decoratedRunnableWithNullMdcContextRunsSuccessfully() throws InterruptedException {
        // No MDC context set — MDC.getCopyOfContextMap() returns null
        MDC.clear();

        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<Boolean> ranSuccessfully = new AtomicReference<>(false);

        Runnable decorated = decorator.decorate(() -> ranSuccessfully.set(true));

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertThat(ranSuccessfully.get()).isTrue();
    }

    @Test
    void decoratedRunnableClearsMdcEvenWhenRunnableThrows() throws InterruptedException {
        MDC.put("correlationId", "caller-id");

        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<Map<String, String>> mdcAfterRun = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> {
            throw new RuntimeException("intentional failure");
        });

        Thread thread = new Thread(() -> {
            try {
                decorated.run();
            } catch (RuntimeException ignored) {
                // expected
            }
            mdcAfterRun.set(MDC.getCopyOfContextMap());
        });
        thread.start();
        thread.join();

        // MDC must be cleared even if the runnable threw
        assertThat(mdcAfterRun.get()).isNullOrEmpty();
    }
}
