package com.dibyanshusinha.apiserv.service.products.repository;

import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.util.ProductSortField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSqlTest {

    @Test
    void findPageOrderedByCreatesExpectedSql() {
        String sql = ProductSql.findPageOrderedBy(ProductSortField.NAME, SortDirection.ASC);
        assertThat(sql).contains("ORDER BY name ASC");
        assertThat(sql).contains("LIMIT :limit OFFSET :offset");
    }

    @Test
    void findPageOrderedByUsesDefaultDescWhenDirectionIsNull() {
        String sql = ProductSql.findPageOrderedBy(ProductSortField.CREATED_AT, null);
        assertThat(sql).contains("ORDER BY created_at DESC");
    }
}
