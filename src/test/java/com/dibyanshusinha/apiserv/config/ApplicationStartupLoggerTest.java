package com.dibyanshusinha.apiserv.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStartupLoggerTest {

    @Test
    void buildStartupUrlsUsesConfiguredPortAndContextPath() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "18080")
                .withProperty("server.servlet.context-path", "/catalog");
        ApplicationStartupLogger startupLogger = new ApplicationStartupLogger(environment, () -> "10.0.0.5");

        ApplicationStartupLogger.StartupUrls startupUrls = startupLogger.buildStartupUrls();

        assertThat(startupUrls.localBaseUrl()).isEqualTo("http://localhost:18080/catalog");
        assertThat(startupUrls.networkBaseUrl()).isEqualTo("http://10.0.0.5:18080/catalog");
    }

    @Test
    void buildStartupUrlsUsesDefaultsWhenPropertiesAreAbsent() {
        ApplicationStartupLogger startupLogger = new ApplicationStartupLogger(new MockEnvironment());

        ApplicationStartupLogger.StartupUrls startupUrls = startupLogger.buildStartupUrls();

        assertThat(startupUrls.localBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(startupUrls.networkBaseUrl()).endsWith(":8080");
    }

    @Test
    void buildStartupUrlsFallsBackToLocalhostWhenHostCannotBeResolved() {
        ApplicationStartupLogger startupLogger = new ApplicationStartupLogger(
                new MockEnvironment(),
                () -> {
                    throw new UnknownHostException("no host");
                });

        ApplicationStartupLogger.StartupUrls startupUrls = startupLogger.buildStartupUrls();

        assertThat(startupUrls.networkBaseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void logApplicationReadyCompletesWithConfiguredEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.port", "18080")
                .withProperty("server.servlet.context-path", "/catalog");
        ApplicationStartupLogger startupLogger = new ApplicationStartupLogger(environment);

        startupLogger.logApplicationReady();

        assertThat(startupLogger.buildStartupUrls().localBaseUrl()).isEqualTo("http://localhost:18080/catalog");
    }
}
