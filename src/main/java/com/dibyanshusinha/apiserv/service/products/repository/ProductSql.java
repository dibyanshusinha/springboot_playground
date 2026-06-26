package com.dibyanshusinha.apiserv.service.products.repository;

import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.util.ProductSortField;

final class ProductSql {

    private static final String TABLE = "products";
    private static final String ROW_COLUMNS = """
            id, name, sku, description, price, active, created_at, updated_at, version
            """;

    static final String INSERT = """
            INSERT INTO %s (name, sku, description, price, active, created_at, updated_at, version)
            VALUES (:name, :sku, :description, :price, :active, :createdAt, :updatedAt, :version)
            """.formatted(TABLE);

    static final String UPDATE = """
            UPDATE %s
            SET name = :name,
                sku = :sku,
                description = :description,
                price = :price,
                active = :active,
                updated_at = :updatedAt,
                version = COALESCE(version, 0) + 1
            WHERE id = :id AND version = :version
            """.formatted(TABLE);

    static final String FIND_BY_ID = """
            SELECT %s
            FROM %s
            WHERE id = :id
            """.formatted(ROW_COLUMNS, TABLE);

    static final String EXISTS_BY_SKU = """
            SELECT EXISTS (
                SELECT 1
                FROM %s
                WHERE sku = :sku
            )
            """.formatted(TABLE);

    static final String EXISTS_BY_SKU_AND_ID_NOT = """
            SELECT EXISTS (
                SELECT 1
                FROM %s
                WHERE sku = :sku
                  AND id <> :id
            )
            """.formatted(TABLE);

    static final String DELETE_BY_ID = """
            DELETE FROM %s
            WHERE id = :id
            """.formatted(TABLE);

    static final String DELETE_ALL = "DELETE FROM " + TABLE;

    static final String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private ProductSql() {
    }

    static String findPageOrderedBy(ProductSortField sortField, SortDirection direction) {
        return """
                SELECT %s
                FROM %s
                ORDER BY %s %s
                LIMIT :limit OFFSET :offset
                """.formatted(ROW_COLUMNS, TABLE, sortField.column(), sortDirection(direction));
    }

    private static String sortDirection(SortDirection direction) {
        if (direction == null) {
            return SortDirection.DESC.getValue();
        }
        return direction.getValue();
    }
}
