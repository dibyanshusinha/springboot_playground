package com.dibyanshusinha.apiserv.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class OpenApiContractController {

    private static final String CONTRACT_RESOURCE = "static/api-contract/openapi.yaml";
    private static final String DESIGN_TIME_SERVERS = """
            servers:
              - url: "{scheme}://{host}:{port}{basePath}"
                description: Target API server for Swagger UI try-it-out.
                variables:
                  scheme:
                    default: http
                    enum:
                      - http
                      - https
                  host:
                    default: localhost
                  port:
                    default: "8080"
                  basePath:
                    default: /
            """;

    @GetMapping(value = "/api-contract/runtime-openapi.yaml", produces = "application/yaml")
    public String runtimeOpenApi(HttpServletRequest request) throws IOException {
        String contract = StreamUtils.copyToString(
                new ClassPathResource(CONTRACT_RESOURCE).getInputStream(),
                StandardCharsets.UTF_8);

        return contract.replace(DESIGN_TIME_SERVERS, runtimeServers(request));
    }

    private String runtimeServers(HttpServletRequest request) {
        return """
                servers:
                  - url: "%s"
                    description: Running Spring Boot application.
                """.formatted(runtimeBaseUrl(request));
    }

    private String runtimeBaseUrl(HttpServletRequest request) {
        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        String host = firstForwardedValue(request.getHeader("X-Forwarded-Host"), request.getServerName());
        String port = firstForwardedValue(request.getHeader("X-Forwarded-Port"), String.valueOf(request.getServerPort()));
        String contextPath = request.getContextPath();

        String hostAndPort = host.contains(":") ? host : host + portSuffix(scheme, port);
        return scheme + "://" + hostAndPort + contextPath;
    }

    private static String firstForwardedValue(String headerValue, String fallbackValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return fallbackValue;
        }
        return headerValue.split(",", 2)[0].trim();
    }

    private static String portSuffix(String scheme, String port) {
        if (("http".equals(scheme) && "80".equals(port)) || ("https".equals(scheme) && "443".equals(port))) {
            return "";
        }
        return ":" + port;
    }
}
