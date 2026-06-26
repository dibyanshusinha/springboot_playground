package com.dibyanshusinha.apiserv.service.products.util;

import java.util.Arrays;
import java.util.Optional;

public enum ProductSortField {
    ID("id", "id"),
    NAME("name", "name"),
    SKU("sku", "sku"),
    PRICE("price", "price"),
    ACTIVE("active", "active"),
    CREATED_AT("createdAt", "created_at"),
    UPDATED_AT("updatedAt", "updated_at");

    private final String property;
    private final String column;

    ProductSortField(String property, String column) {
        this.property = property;
        this.column = column;
    }

    public String column() {
        return column;
    }

    public static Optional<ProductSortField> fromProperty(String property) {
        return Arrays.stream(values())
                .filter(sort -> sort.property.equals(property))
                .findFirst();
    }
}
