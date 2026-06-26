package com.dibyanshusinha.apiserv.service.products.repository;

import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.entity.ProductRow;
import com.dibyanshusinha.apiserv.service.products.util.ProductSortField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link ProductRepository} — no Docker or live DB required.
 * Uses Mockito to mock {@link NamedParameterJdbcTemplate} and captures
 * the {@link RowMapper} method reference to exercise {@code mapProduct} branches.
 */
@ExtendWith(MockitoExtension.class)
class ProductRepositoryTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  create()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createProductSetsIdFromKeyHolderWhenKeyIsReturned() throws Exception {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        // Simulate JDBC populating the GeneratedKeyHolder with the new row's ID
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(2);
            // Inject the generated key using reflection (matches real JDBC driver behaviour)
            java.lang.reflect.Field keyListField = GeneratedKeyHolder.class.getDeclaredField("keyList");
            keyListField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> keyList =
                    (java.util.List<Map<String, Object>>) keyListField.get(keyHolder);
            keyList.add(Map.of("id", 99L));
            return 1;
        }).when(mockTemplate).update(
                eq(ProductSql.INSERT),
                any(MapSqlParameterSource.class),
                any(KeyHolder.class),
                any(String[].class));

        ProductRow product = sampleProduct();
        ProductRow created = repository.create(product);

        assertThat(created.getId()).isEqualTo(99L);
        assertThat(created.getVersion()).isEqualTo(0L);
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void createProductLeavesIdNullWhenKeyHolderReturnsNoKey() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        // default mock: update() does nothing → key holder has no key
        ProductRow created = repository.create(sampleProduct());

        assertThat(created.getId()).isNull();
        assertThat(created.getVersion()).isEqualTo(0L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  update()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateProductReturnsRefetchedRowWhenUpdateSucceeds() throws Exception {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        // 1 row affected → successful update
        when(mockTemplate.update(eq(ProductSql.UPDATE), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        // findById internally calls query(...) — return the updated row via captured RowMapper
        doAnswer(invocation -> {
            RowMapper<ProductRow> mapper = invocation.getArgument(2);
            ResultSet rs = mockResultSet("Updated Name", false);
            return List.of(mapper.mapRow(rs, 0));
        }).when(mockTemplate).query(eq(ProductSql.FIND_BY_ID), anyMap(), any(RowMapper.class));

        ProductRow product = sampleProduct();
        product.setId(1L);
        product.setVersion(0L);

        ProductRow result = repository.update(product);
        assertThat(result.getName()).isEqualTo("Updated Name");
    }

    @Test
    void updateProductThrowsOptimisticLockingWhenNoRowsAffected() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.update(eq(ProductSql.UPDATE), any(MapSqlParameterSource.class)))
                .thenReturn(0);

        ProductRow product = sampleProduct();
        product.setId(42L);
        product.setVersion(5L);

        assertThatThrownBy(() -> repository.update(product))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("42")
                .hasMessageContaining("5");
    }

    @Test
    void updateProductFallsBackToInputProductWhenFindByIdReturnsEmpty() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.update(eq(ProductSql.UPDATE), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        // findById returns no results → Optional.empty() → orElse(product)
        when(mockTemplate.query(eq(ProductSql.FIND_BY_ID), anyMap(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        ProductRow product = sampleProduct();
        product.setId(7L);
        product.setVersion(2L);

        ProductRow result = repository.update(product);
        assertThat(result).isSameAs(product);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  mapProduct (private static) — tested via captured RowMapper reference
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void mapProductMapsAllFieldsCorrectlyWhenVersionIsNotNull() throws Exception {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        doAnswer(invocation -> {
            RowMapper<ProductRow> mapper = invocation.getArgument(2);
            ResultSet rs = mockResultSet("Keyboard", false); // wasNull = false → version set
            return List.of(mapper.mapRow(rs, 0));
        }).when(mockTemplate).query(eq(ProductSql.FIND_BY_ID), anyMap(), any(RowMapper.class));

        var found = repository.findById(1L);

        assertThat(found).isPresent();
        ProductRow row = found.get();
        assertThat(row.getId()).isEqualTo(1L);
        assertThat(row.getName()).isEqualTo("Keyboard");
        assertThat(row.getSku()).isEqualTo("SKU-1");
        assertThat(row.getDescription()).isEqualTo("A nice keyboard");
        assertThat(row.getPrice()).isEqualByComparingTo("99.99");
        assertThat(row.isActive()).isTrue();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getUpdatedAt()).isNotNull();
        assertThat(row.getVersion()).isEqualTo(3L); // non-null version
    }

    @Test
    void mapProductSetsVersionNullWhenResultSetWasNull() throws Exception {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        doAnswer(invocation -> {
            RowMapper<ProductRow> mapper = invocation.getArgument(2);
            ResultSet rs = mockResultSet("Mouse", true); // wasNull = true → version null
            return List.of(mapper.mapRow(rs, 0));
        }).when(mockTemplate).query(eq(ProductSql.FIND_BY_ID), anyMap(), any(RowMapper.class));

        var found = repository.findById(2L);

        assertThat(found).isPresent();
        assertThat(found.get().getVersion()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  findAll() / count()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void countReturnsZeroWhenTemplateReturnsNull() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(eq(ProductSql.COUNT), anyMap(), eq(Long.class)))
                .thenReturn(null);

        ProductRepository.ProductPage page =
                repository.findAll(0, 10, ProductSortField.NAME, SortDirection.ASC);
        assertThat(page.totalElements()).isZero();
        assertThat(page.content()).isEmpty();
    }

    @Test
    void findAllWithMappedResultsViaRowMapper() throws Exception {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(eq(ProductSql.COUNT), anyMap(), eq(Long.class)))
                .thenReturn(1L);

        doAnswer(invocation -> {
            RowMapper<ProductRow> mapper = invocation.getArgument(2);
            ResultSet rs = mockResultSet("Headset", false);
            return List.of(mapper.mapRow(rs, 0));
        }).when(mockTemplate).query(anyString(), anyMap(), any(RowMapper.class));

        ProductRepository.ProductPage page =
                repository.findAll(0, 10, ProductSortField.NAME, SortDirection.ASC);

        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).getName()).isEqualTo("Headset");
        assertThat(page.last()).isTrue();
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void findAllComputesLastFlagCorrectly() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(eq(ProductSql.COUNT), anyMap(), eq(Long.class)))
                .thenReturn(10L);
        when(mockTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        // Page 3 (0-indexed) with pageSize=3 → 4 pages total → page 3 IS the last
        ProductRepository.ProductPage lastPage =
                repository.findAll(3, 3, ProductSortField.NAME, SortDirection.ASC);
        assertThat(lastPage.last()).isTrue();
        assertThat(lastPage.totalPages()).isEqualTo(4);

        // Page 0 should NOT be last
        ProductRepository.ProductPage firstPage =
                repository.findAll(0, 3, ProductSortField.NAME, SortDirection.ASC);
        assertThat(firstPage.last()).isFalse();
    }

    @Test
    void findByIdReturnsEmptyWhenNoResultFound() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.query(eq(ProductSql.FIND_BY_ID), anyMap(), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        assertThat(repository.findById(999L)).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  existsBySku / existsBySkuAndIdNot
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void existsBySkuReturnsTrueWhenFound() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(eq(ProductSql.EXISTS_BY_SKU), anyMap(), eq(Boolean.class)))
                .thenReturn(true);

        assertThat(repository.existsBySku("SKU-EXISTS")).isTrue();
    }

    @Test
    void existsBySkuReturnsFalseWhenTemplateReturnsNull() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(eq(ProductSql.EXISTS_BY_SKU), anyMap(), eq(Boolean.class)))
                .thenReturn(null);

        assertThat(repository.existsBySku("SKU-WHATEVER")).isFalse();
    }

    @Test
    void existsBySkuAndIdNotReturnsTrueWhenExists() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(
                eq(ProductSql.EXISTS_BY_SKU_AND_ID_NOT), anyMap(), eq(Boolean.class)))
                .thenReturn(true);

        assertThat(repository.existsBySkuAndIdNot("SKU-1", 99L)).isTrue();
    }

    @Test
    void existsBySkuAndIdNotReturnsFalseWhenTemplateReturnsNull() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        when(mockTemplate.queryForObject(
                eq(ProductSql.EXISTS_BY_SKU_AND_ID_NOT), anyMap(), eq(Boolean.class)))
                .thenReturn(null);

        assertThat(repository.existsBySkuAndIdNot("SKU-1", 99L)).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  delete / deleteById / deleteAll
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deleteByIdDelegatesToJdbcTemplate() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        repository.deleteById(123L);

        verify(mockTemplate).update(eq(ProductSql.DELETE_BY_ID), anyMap());
    }

    @Test
    void deleteAllDelegatesToJdbcTemplate() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        repository.deleteAll();

        verify(mockTemplate).update(eq(ProductSql.DELETE_ALL), anyMap());
    }

    @Test
    void deleteProductDelegatesToDeleteById() {
        NamedParameterJdbcTemplate mockTemplate = mock(NamedParameterJdbcTemplate.class);
        ProductRepository repository = new ProductRepository(mockTemplate);

        ProductRow product = new ProductRow();
        product.setId(77L);

        repository.delete(product);

        verify(mockTemplate).update(eq(ProductSql.DELETE_BY_ID), anyMap());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static ProductRow sampleProduct() {
        ProductRow p = new ProductRow();
        p.setName("Test Product");
        p.setSku("SKU-1");
        p.setDescription("A description");
        p.setPrice(BigDecimal.TEN);
        p.setActive(true);
        return p;
    }

    /**
     * Builds a fully-configured mock {@link ResultSet} that returns realistic values.
     *
     * @param name     value for the {@code name} column
     * @param versionWasNull if {@code true}, {@code wasNull()} returns {@code true} after the
     *                       version column is read, simulating a SQL NULL in the version column
     */
    private static ResultSet mockResultSet(String name, boolean versionWasNull) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getString("sku")).thenReturn("SKU-1");
        when(rs.getString("description")).thenReturn("A nice keyboard");
        when(rs.getBigDecimal("price")).thenReturn(new BigDecimal("99.99"));
        when(rs.getBoolean("active")).thenReturn(true);
        Timestamp now = Timestamp.from(Instant.now());
        when(rs.getTimestamp("created_at")).thenReturn(now);
        when(rs.getTimestamp("updated_at")).thenReturn(now);
        when(rs.getLong("version")).thenReturn(versionWasNull ? 0L : 3L);
        when(rs.wasNull()).thenReturn(versionWasNull);
        return rs;
    }
}
