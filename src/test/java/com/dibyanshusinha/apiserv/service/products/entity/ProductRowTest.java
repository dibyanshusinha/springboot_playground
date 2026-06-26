package com.dibyanshusinha.apiserv.service.products.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRowTest {

    @Test
    void defaultsActiveToTrue() {
        ProductRow product = product();

        assertThat(product.isActive()).isTrue();
    }

    @Test
    void settersUpdateProductFields() {
        ProductRow product = product();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        product.setId(10L);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        product.setVersion(2L);

        assertThat(product.getId()).isEqualTo(10L);
        assertThat(product.getName()).isEqualTo("Keyboard");
        assertThat(product.getSku()).isEqualTo("KEY-ENT-001");
        assertThat(product.getDescription()).isEqualTo("Test product");
        assertThat(product.getPrice()).isEqualByComparingTo("49.99");
        assertThat(product.isActive()).isTrue();
        assertThat(product.getCreatedAt()).isEqualTo(now);
        assertThat(product.getUpdatedAt()).isEqualTo(now);
        assertThat(product.getVersion()).isEqualTo(2L);
    }

    private ProductRow product() {
        ProductRow product = new ProductRow();
        product.setName("Keyboard");
        product.setSku("KEY-ENT-001");
        product.setDescription("Test product");
        product.setPrice(BigDecimal.valueOf(49.99));
        product.setActive(true);
        return product;
    }
}
