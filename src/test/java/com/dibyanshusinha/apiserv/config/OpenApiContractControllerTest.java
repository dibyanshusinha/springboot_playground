package com.dibyanshusinha.apiserv.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractControllerTest {

    private final OpenApiContractController controller = new OpenApiContractController();

    @Test
    void runtimeOpenApiUsesCurrentRequestAsServerUrl() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.setContextPath("/catalog");

        String openApi = controller.runtimeOpenApi(request);

        assertThat(openApi)
                .contains("url: \"http://localhost:8080/catalog\"")
                .contains("description: Running Spring Boot application.")
                .contains("$ref: './components/products/schemas.yaml#/ProductPage'")
                .doesNotContain("Target API server for Swagger UI try-it-out.");
    }

    @Test
    void runtimeOpenApiHonorsForwardedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "api.example.com");
        request.addHeader("X-Forwarded-Port", "443");

        String openApi = controller.runtimeOpenApi(request);

        assertThat(openApi).contains("url: \"https://api.example.com\"");
    }
}
