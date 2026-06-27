package com.dibyanshusinha.apiserv.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocsControllerTest {

    @Test
    void apiDocsRedirectsToPackagedSwaggerUiWithRuntimeContract() {
        ApiDocsController controller = new ApiDocsController();

        String redirect = controller.apiDocs();

        assertThat(redirect).isEqualTo("redirect:/api_docs/index.html?url=/api-contract/openapi.yaml&runtimeServer=true");
    }
}
