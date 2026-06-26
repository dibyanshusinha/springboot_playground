package com.dibyanshusinha.apiserv.service.products.mapper;

import com.dibyanshusinha.apiserv.generated.model.ProductCreateRequest;
import com.dibyanshusinha.apiserv.generated.model.ProductResponse;
import com.dibyanshusinha.apiserv.generated.model.ProductUpdateRequest;
import com.dibyanshusinha.apiserv.service.products.entity.ProductRow;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void toRowDefaultsActiveToTrueWhenRequestActiveIsNull() {
        ProductCreateRequest request = new ProductCreateRequest("Keyboard", "KEY-MAP-001", BigDecimal.valueOf(49.99))
                .description("Test product")
                .active(null);

        ProductRow product = mapper.toRow(request);

        assertThat(product.getName()).isEqualTo("Keyboard");
        assertThat(product.getSku()).isEqualTo("KEY-MAP-001");
        assertThat(product.getDescription()).isEqualTo("Test product");
        assertThat(product.getPrice()).isEqualByComparingTo("49.99");
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void toRowUsesRequestActiveFlagWhenPresent() {
        ProductCreateRequest request = new ProductCreateRequest("Keyboard", "KEY-MAP-001", BigDecimal.valueOf(49.99))
                .description("Test product")
                .active(false);

        ProductRow product = mapper.toRow(request);

        assertThat(product.isActive()).isFalse();
    }

    @Test
    void toRowUsesTrueActiveFlagWhenPresent() {
        ProductCreateRequest request = new ProductCreateRequest("Keyboard", "KEY-MAP-002", BigDecimal.valueOf(49.99))
                .description("Test product")
                .active(true);

        ProductRow product = mapper.toRow(request);

        assertThat(product.isActive()).isTrue();
    }

    @Test
    void updateRowCopiesMutableFields() {
        ProductRow product = new ProductRow();
        ProductUpdateRequest request = new ProductUpdateRequest()
                .name("Monitor")
                .sku("MON-MAP-001")
                .price(BigDecimal.valueOf(199.99))
                .active(false)
                .description("Updated");

        mapper.updateRow(product, request);

        assertThat(product.getName()).isEqualTo("Monitor");
        assertThat(product.getSku()).isEqualTo("MON-MAP-001");
        assertThat(product.getDescription()).isEqualTo("Updated");
        assertThat(product.getPrice()).isEqualByComparingTo("199.99");
        assertThat(product.isActive()).isFalse();
    }

    @Test
    void toResponseCopiesProductFields() {
        ProductRow product = new ProductRow();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        product.setId(10L);
        product.setName("Mouse");
        product.setSku("MOU-MAP-001");
        product.setDescription("Test product");
        product.setPrice(BigDecimal.valueOf(29.99));
        product.setActive(true);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Mouse");
        assertThat(response.getSku()).isEqualTo("MOU-MAP-001");
        assertThat(response.getDescription()).isEqualTo("Test product");
        assertThat(response.getPrice()).isEqualByComparingTo("29.99");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getCreatedAt().toInstant()).isEqualTo(product.getCreatedAt());
        assertThat(response.getUpdatedAt().toInstant()).isEqualTo(product.getUpdatedAt());
    }

    @Test
    void toOffsetDateTimeReturnsNullWhenInstantIsNull() {
        assertThat(mapper.toOffsetDateTime(null)).isNull();
    }

    @Test
    void updateRowSelectiveUpdatesOnlySpecifiedMaskFields() {
        ProductRow product = new ProductRow();
        product.setName("Original Name");
        product.setSku("ORIG-001");
        product.setDescription("Original Desc");
        product.setPrice(BigDecimal.valueOf(10.00));
        product.setActive(true);

        ProductUpdateRequest request = new ProductUpdateRequest()
                .name("Updated Name")
                .sku("NEW-SKU-001")
                .description("Updated Desc")
                .price(BigDecimal.valueOf(15.00))
                .active(false);

        // Update name and price only
        mapper.updateRowSelective(product, request, "name, price");

        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getPrice()).isEqualByComparingTo("15.00");
        
        // Sku, active, description should not be updated
        assertThat(product.getSku()).isEqualTo("ORIG-001");
        assertThat(product.isActive()).isTrue();
        assertThat(product.getDescription()).isEqualTo("Original Desc");
    }

    @Test
    void updateRowSelectiveUpdatesNonNullFieldsWhenMaskIsEmpty() {
        ProductRow product = new ProductRow();
        product.setName("Original Name");
        product.setSku("ORIG-001");
        product.setDescription("Original Desc");
        product.setPrice(BigDecimal.valueOf(10.00));
        product.setActive(true);

        ProductUpdateRequest request = new ProductUpdateRequest()
                .name("Updated Name")
                .price(BigDecimal.valueOf(15.00)); // active and description are null

        mapper.updateRowSelective(product, request, null);

        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getPrice()).isEqualByComparingTo("15.00");
        
        // sku, active, description remain unchanged (not nulled out)
        assertThat(product.getSku()).isEqualTo("ORIG-001");
        assertThat(product.isActive()).isTrue();
        assertThat(product.getDescription()).isEqualTo("Original Desc");
    }

    @Test
    void updateRowSelectiveWithWhitespaceMaskBehavesLikeNull() {
        ProductRow product = new ProductRow();
        product.setName("Original Name");
        product.setSku("ORIG-001");
        product.setDescription("Original Desc");
        product.setPrice(BigDecimal.valueOf(10.00));
        product.setActive(true);

        ProductUpdateRequest request = new ProductUpdateRequest()
                .name("Updated Name")
                .sku("NEW-SKU-001");

        mapper.updateRowSelective(product, request, "   ");

        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getSku()).isEqualTo("NEW-SKU-001");
        assertThat(product.getDescription()).isEqualTo("Original Desc");
    }

    @Test
    void updateRowSelectiveSpecifiesAllFieldsInMask() {
        ProductRow product = new ProductRow();
        product.setName("Original Name");
        product.setSku("ORIG-001");
        product.setDescription("Original Desc");
        product.setPrice(BigDecimal.valueOf(10.00));
        product.setActive(true);

        ProductUpdateRequest request = new ProductUpdateRequest()
                .name("Updated Name")
                .sku("NEW-SKU-001")
                .description("Updated Desc")
                .price(BigDecimal.valueOf(15.00))
                .active(false);

        mapper.updateRowSelective(product, request, "name,sku,description,price,active");

        assertThat(product.getName()).isEqualTo("Updated Name");
        assertThat(product.getSku()).isEqualTo("NEW-SKU-001");
        assertThat(product.getDescription()).isEqualTo("Updated Desc");
        assertThat(product.getPrice()).isEqualByComparingTo("15.00");
        assertThat(product.isActive()).isFalse();
    }

    @Test
    void updateRowSelectiveActiveFieldInMaskButNullInRequest() {
        ProductRow product = new ProductRow();
        product.setActive(true);

        ProductUpdateRequest request = new ProductUpdateRequest()
                .active(null); // Explicitly null in request

        mapper.updateRowSelective(product, request, "active");

        // Should not update since request is null
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void updateRowSelectiveUpdatesOtherNonNullFieldsWhenMaskIsEmpty() {
        ProductRow product = new ProductRow();
        product.setName("Original Name");
        product.setSku("ORIG-001");
        product.setDescription("Original Desc");
        product.setPrice(BigDecimal.valueOf(10.00));
        product.setActive(true);

        ProductUpdateRequest request = new ProductUpdateRequest()
                .name(null)
                .sku("NEW-SKU-001")
                .description("NEW-DESC")
                .price(null)
                .active(false);

        mapper.updateRowSelective(product, request, null);

        // name and price remain unchanged
        assertThat(product.getName()).isEqualTo("Original Name");
        assertThat(product.getPrice()).isEqualByComparingTo("10.00");

        // sku, description, active should be updated
        assertThat(product.getSku()).isEqualTo("NEW-SKU-001");
        assertThat(product.getDescription()).isEqualTo("NEW-DESC");
        assertThat(product.isActive()).isFalse();
    }

    @Test
    void toRowReturnsNullWhenRequestIsNull() {
        assertThat(mapper.toRow(null)).isNull();
    }

    @Test
    void updateRowDoesNothingWhenRequestIsNull() {
        ProductRow product = new ProductRow();
        product.setName("Original");
        mapper.updateRow(product, null);
        assertThat(product.getName()).isEqualTo("Original");
    }

    @Test
    void toResponseReturnsNullWhenProductIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
