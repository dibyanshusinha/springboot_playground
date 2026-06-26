package com.dibyanshusinha.apiserv.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ApplicationStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupLogger.class);

    private final Environment environment;
    private final HostAddressProvider hostAddressProvider;

    @Autowired
    public ApplicationStartupLogger(Environment environment) {
        this(environment, () -> InetAddress.getLocalHost().getHostAddress());
    }

    ApplicationStartupLogger(Environment environment, HostAddressProvider hostAddressProvider) {
        this.environment = environment;
        this.hostAddressProvider = hostAddressProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationReady() {
        StartupUrls startupUrls = buildStartupUrls();

        log.info("""

                ----------------------------------------------------------
                Product API is running.
                Local API:      {}
                Network API:    {}
                Swagger UI:     {}/swagger-ui.html
                OpenAPI JSON:   {}/v3/api-docs
                Health Check:   {}/actuator/health
                ----------------------------------------------------------
                """, startupUrls.localBaseUrl(), startupUrls.networkBaseUrl(), startupUrls.localBaseUrl(), startupUrls.localBaseUrl(), startupUrls.localBaseUrl());
    }

    StartupUrls buildStartupUrls() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String hostAddress = getHostAddress();
        String localBaseUrl = "http://localhost:" + port + contextPath;
        String networkBaseUrl = "http://" + hostAddress + ":" + port + contextPath;
        return new StartupUrls(localBaseUrl, networkBaseUrl);
    }

    private String getHostAddress() {
        try {
            return hostAddressProvider.getHostAddress();
        } catch (UnknownHostException exception) {
            return "localhost";
        }
    }

    record StartupUrls(String localBaseUrl, String networkBaseUrl) {
    }

    interface HostAddressProvider {
        String getHostAddress() throws UnknownHostException;
    }
}
