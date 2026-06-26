package com.dibyanshusinha.apiserv.service.products.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSortFieldTest {

    @Test
    void fromPropertyReturnsExpectedEnum() {
        Optional<ProductSortField> field = ProductSortField.fromProperty("createdAt");
        assertThat(field).isPresent().contains(ProductSortField.CREATED_AT);
        assertThat(ProductSortField.CREATED_AT.column()).isEqualTo("created_at");
    }

    @Test
    void fromPropertyReturnsEmptyOptionalForUnknownProperty() {
        Optional<ProductSortField> field = ProductSortField.fromProperty("unknown");
        assertThat(field).isEmpty();
    }
}
