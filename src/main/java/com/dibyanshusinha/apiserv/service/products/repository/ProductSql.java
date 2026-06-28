package com.dibyanshusinha.apiserv.service.products.repository;

import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.util.ProductSortField;
import org.springframework.lang.NonNull;
import java.util.Objects;

final class ProductSql {

    private static final String TABLE = "products";
    private static final String ROW_COLUMNS = "id, name, sku, description, price, active, created_at, updated_at, version";

    static final @NonNull String INSERT =
            "INSERT INTO " + TABLE + " (name, sku, description, price, active, created_at, updated_at, version) " +
            "VALUES (:name, :sku, :description, :price, :active, :createdAt, :updatedAt, :version)";

    static final @NonNull String UPDATE =
            "UPDATE " + TABLE + " " +
            "SET name = :name, " +
            "    sku = :sku, " +
            "    description = :description, " +
            "    price = :price, " +
            "    active = :active, " +
            "    updated_at = :updatedAt, " +
            "    version = COALESCE(version, 0) + 1 " +
            "WHERE id = :id AND version = :version";

    static final @NonNull String FIND_BY_ID =
            "SELECT " + ROW_COLUMNS + " " +
            "FROM " + TABLE + " " +
            "WHERE id = :id";

    static final @NonNull String EXISTS_BY_SKU =
            "SELECT EXISTS ( " +
            "    SELECT 1 " +
            "    FROM " + TABLE + " " +
            "    WHERE sku = :sku " +
            ")";

    static final @NonNull String EXISTS_BY_SKU_AND_ID_NOT =
            "SELECT EXISTS ( " +
            "    SELECT 1 " +
            "    FROM " + TABLE + " " +
            "    WHERE sku = :sku " +
            "      AND id <> :id " +
            ")";

    static final @NonNull String DELETE_BY_ID =
            "DELETE FROM " + TABLE + " " +
            "WHERE id = :id";

    static final @NonNull String DELETE_ALL = "DELETE FROM " + TABLE;

    static final @NonNull String COUNT = "SELECT COUNT(*) FROM " + TABLE;

    private ProductSql() {
    }

    static @NonNull String findPageOrderedBy(ProductSortField sortField, SortDirection direction) {
        return "SELECT " + ROW_COLUMNS + " " +
                "FROM " + TABLE + " " +
                "ORDER BY " + sortField.column() + " " + sortDirection(direction) + " " +
                "LIMIT :limit OFFSET :offset";
    }

    private static @NonNull String sortDirection(SortDirection direction) {
        if (direction == null) {
            return Objects.requireNonNull(SortDirection.DESC.getValue());
        }
        return Objects.requireNonNull(direction.getValue());
    }
}
