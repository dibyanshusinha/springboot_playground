package com.dibyanshusinha.apiserv.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SwaggerConfigTest {

    private final SwaggerConfig swaggerConfig = new SwaggerConfig();

    @Test
    void swaggerIndexPageTransformerIsCreated() {
        SwaggerIndexPageTransformer transformer = swaggerConfig.swaggerIndexPageTransformer(
                mock(SwaggerUiConfigProperties.class),
                mock(SwaggerUiOAuthProperties.class),
                mock(SwaggerUiConfigParameters.class),
                mock(SwaggerWelcomeCommon.class),
                mock(ObjectMapperProvider.class)
        );
        assertThat(transformer).isNotNull();
    }

    @Test
    void transformBypassesNonIndexHtmlFiles() throws IOException {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
        SwaggerUiOAuthProperties oauthProperties = new SwaggerUiOAuthProperties();
        SwaggerUiConfigParameters configParameters = new SwaggerUiConfigParameters(properties);
        SwaggerWelcomeCommon welcomeCommon = mock(SwaggerWelcomeCommon.class);
        ObjectMapperProvider objectMapperProvider = mock(ObjectMapperProvider.class);

        SwaggerIndexPageTransformer transformer = swaggerConfig.swaggerIndexPageTransformer(
                properties, oauthProperties, configParameters, welcomeCommon, objectMapperProvider
        );

        HttpServletRequest request = mock(HttpServletRequest.class);
        Resource original = mock(Resource.class);
        ResourceTransformerChain chain = mock(ResourceTransformerChain.class);

        java.net.URL dummyUrl = java.net.URI.create("http://localhost/swagger-ui/other.txt").toURL();
        when(original.getURL()).thenReturn(dummyUrl);

        // When filename is null
        when(original.getFilename()).thenReturn(null);
        Resource dummyTransformed = mock(Resource.class);
        when(chain.transform(request, original)).thenReturn(dummyTransformed);

        Resource resultNull = transformer.transform(request, original, chain);
        assertThat(resultNull).isEqualTo(original);

        // When filename is other.txt
        when(original.getFilename()).thenReturn("other.txt");
        Resource resultOther = transformer.transform(request, original, chain);
        assertThat(resultOther).isEqualTo(original);
    }

    @Test
    void transformModifiesIndexHtml() throws IOException {
        SwaggerUiConfigProperties properties = new SwaggerUiConfigProperties();
        SwaggerUiOAuthProperties oauthProperties = new SwaggerUiOAuthProperties();
        SwaggerUiConfigParameters configParameters = mock(SwaggerUiConfigParameters.class);
        SwaggerWelcomeCommon welcomeCommon = mock(SwaggerWelcomeCommon.class);
        ObjectMapperProvider objectMapperProvider = mock(ObjectMapperProvider.class);

        SwaggerIndexPageTransformer transformer = swaggerConfig.swaggerIndexPageTransformer(
                properties, oauthProperties, configParameters, welcomeCommon, objectMapperProvider
        );

        HttpServletRequest request = mock(HttpServletRequest.class);
        Resource original = mock(Resource.class);
        ResourceTransformerChain chain = mock(ResourceTransformerChain.class);

        when(original.getFilename()).thenReturn("index.html");
        
        java.net.URL dummyUrl = java.net.URI.create("http://localhost/swagger-ui/index.html").toURL();
        when(original.getURL()).thenReturn(dummyUrl);
        
        // Mock getInputStream of original file containing HTML structure
        String htmlContent = "<html><head></head><body>swagger-ui</body></html>";
        when(original.getInputStream()).thenReturn(new ByteArrayInputStream(htmlContent.getBytes(StandardCharsets.UTF_8)));

        // SwaggerIndexPageTransformer might call createRelative or check properties, so let's mock it
        when(original.createRelative(any())).thenReturn(original);

        Resource result = transformer.transform(request, original, chain);

        assertThat(result).isNotNull();
        String resultHtml = new String(result.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(resultHtml).contains("display: none !important;");
        assertThat(resultHtml).contains("</head>");
    }
}
