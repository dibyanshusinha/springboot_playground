package com.dibyanshusinha.apiserv.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {SecurityConfig.class, SecurityConfigTest.MvcTestConfig.class})
class SecurityConfigTest {

    @Autowired(required = false)
    private FilterChainProxy filterChainProxy;

    @Test
    void securityConfigIsLoaded() {
        assertThat(filterChainProxy).isNotNull();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MvcTestConfig {

        @Bean(name = "mvcHandlerMappingIntrospector")
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }
    }
}
