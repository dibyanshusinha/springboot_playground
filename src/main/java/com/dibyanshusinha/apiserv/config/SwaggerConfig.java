package com.dibyanshusinha.apiserv.config;

import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerConfig {

    @Bean
    public SwaggerIndexPageTransformer swaggerIndexPageTransformer(
            SwaggerUiConfigProperties swaggerUiConfigProperties,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerUiConfigParameters swaggerUiConfigParameters,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider) {

        return new SwaggerIndexPageTransformer(
                swaggerUiConfigProperties,
                swaggerUiOAuthProperties,
                swaggerUiConfigParameters,
                swaggerWelcomeCommon,
                objectMapperProvider) {

            @Override
            public Resource transform(HttpServletRequest request, Resource resource,
                                      ResourceTransformerChain transformerChain) throws IOException {
                Resource transformed = super.transform(request, resource, transformerChain);
                if (resource.getFilename() != null && resource.getFilename().equals("index.html")) {
                    String html = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    // Inject a style block to hide the spec link entirely
                    String styleBlock = "<style>"
                            + ".swagger-ui .info .url { display: none !important; }"
                            + "</style>";
                    html = html.replace("</head>", styleBlock + "</head>");
                    return new TransformedResource(resource, html.getBytes(StandardCharsets.UTF_8));
                }
                return transformed;
            }
        };
    }
}
