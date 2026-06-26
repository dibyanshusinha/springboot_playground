package com.dibyanshusinha.apiserv.service.products.repository;

import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.entity.ProductRow;
import com.dibyanshusinha.apiserv.service.products.util.ProductSortField;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProductRow create(ProductRow product) {
        Instant now = Instant.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = parameters(product)
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(now))
                .addValue("version", 0L);

        jdbcTemplate.update(ProductSql.INSERT,
                parameters,
                keyHolder,
                new String[]{"id"});

        Number id = keyHolder.getKey();
        if (id != null) {
            product.setId(id.longValue());
        }
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        product.setVersion(0L);
        return product;
    }

    public ProductRow update(ProductRow product) {
        Instant now = Instant.now();
        MapSqlParameterSource parameters = parameters(product)
                .addValue("id", product.getId())
                .addValue("version", product.getVersion())
                .addValue("updatedAt", Timestamp.from(now));

        int updated = jdbcTemplate.update(ProductSql.UPDATE, parameters);
        if (updated == 0) {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "Product with ID " + product.getId() + " and version " + product.getVersion() + " was updated or deleted concurrently.");
        }

        return findById(product.getId()).orElse(product);
    }

    public Optional<ProductRow> findById(Long id) {
        List<ProductRow> products = jdbcTemplate.query(ProductSql.FIND_BY_ID,
                Map.of("id", id),
                ProductRepository::mapProduct);
        return Optional.ofNullable(DataAccessUtils.singleResult(products));
    }

    public ProductPage findAll(int page, int size, ProductSortField sortField, SortDirection direction) {
        long totalElements = count();
        int offset = page * size;

        List<ProductRow> products = jdbcTemplate.query(ProductSql.findPageOrderedBy(sortField, direction),
                Map.of("limit", size, "offset", offset),
                ProductRepository::mapProduct);

        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean last = page >= Math.max(totalPages - 1, 0);
        return new ProductPage(products, page, size, totalElements, totalPages, last);
    }

    public boolean existsBySku(String sku) {
        Boolean exists = jdbcTemplate.queryForObject(
                ProductSql.EXISTS_BY_SKU,
                Map.of("sku", sku),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsBySkuAndIdNot(String sku, Long id) {
        Boolean exists = jdbcTemplate.queryForObject(
                ProductSql.EXISTS_BY_SKU_AND_ID_NOT,
                Map.of("sku", sku, "id", id),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public void delete(ProductRow product) {
        deleteById(product.getId());
    }

    public void deleteById(Long id) {
        jdbcTemplate.update(ProductSql.DELETE_BY_ID, Map.of("id", id));
    }

    public void deleteAll() {
        jdbcTemplate.update(ProductSql.DELETE_ALL, Map.of());
    }

    private long count() {
        Long count = jdbcTemplate.queryForObject(ProductSql.COUNT, Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    private MapSqlParameterSource parameters(ProductRow product) {
        return new MapSqlParameterSource()
                .addValue("name", product.getName())
                .addValue("sku", product.getSku())
                .addValue("description", product.getDescription())
                .addValue("price", product.getPrice())
                .addValue("active", product.isActive());
    }

    private static ProductRow mapProduct(ResultSet resultSet, int rowNumber) throws SQLException {
        ProductRow product = new ProductRow();
        product.setId(resultSet.getLong("id"));
        product.setName(resultSet.getString("name"));
        product.setSku(resultSet.getString("sku"));
        product.setDescription(resultSet.getString("description"));
        product.setPrice(resultSet.getBigDecimal("price"));
        product.setActive(resultSet.getBoolean("active"));
        product.setCreatedAt(resultSet.getTimestamp("created_at").toInstant());
        product.setUpdatedAt(resultSet.getTimestamp("updated_at").toInstant());
        long version = resultSet.getLong("version");
        product.setVersion(resultSet.wasNull() ? null : version);
        return product;
    }

    public record ProductPage(
            List<ProductRow> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {
    }
}
