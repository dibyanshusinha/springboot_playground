package com.dibyanshusinha.apiserv.service.products;

import com.dibyanshusinha.apiserv.generated.model.ProductCreateRequest;
import com.dibyanshusinha.apiserv.generated.model.ProductPage;
import com.dibyanshusinha.apiserv.generated.model.ProductResponse;
import com.dibyanshusinha.apiserv.generated.model.ProductUpdateRequest;
import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.entity.ProductRow;
import com.dibyanshusinha.apiserv.service.products.exception.ProductException;
import com.dibyanshusinha.apiserv.service.products.mapper.ProductMapper;
import com.dibyanshusinha.apiserv.service.products.repository.ProductRepository;
import com.dibyanshusinha.apiserv.service.products.util.ProductSortField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, Mappers.getMapper(ProductMapper.class));
    }

    @Test
    void createProductReturnsSavedProduct() {
        ProductCreateRequest request = createRequest("Mechanical Keyboard", "KEY-UT-001");
        ProductRow savedProduct = product(1L, "Mechanical Keyboard", "KEY-UT-001");

        when(productRepository.existsBySku("KEY-UT-001")).thenReturn(false);
        when(productRepository.create(any(ProductRow.class))).thenReturn(savedProduct);

        ProductResponse result = productService.createProduct(request);

        assertResponse(result, 1L, "Mechanical Keyboard", "KEY-UT-001");
        verify(productRepository).create(argThat(product ->
                product.getId() == null
                        && product.getName().equals("Mechanical Keyboard")
                        && product.getSku().equals("KEY-UT-001")
                        && product.getDescription().equals("Test product")
                        && product.getPrice().compareTo(BigDecimal.valueOf(99.99)) == 0
                        && product.isActive()));
    }

    @Test
    void createProductRejectsDuplicateSku() {
        ProductCreateRequest request = createRequest("Mechanical Keyboard", "KEY-UT-001");
        when(productRepository.existsBySku("KEY-UT-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ProductException.class)
                .hasMessage("Product SKU already exists: KEY-UT-001");
    }

    @Test
    void listProductsReturnsStableProductPage() {
        ProductRow product = product(10L, "USB-C Hub", "HUB-UT-001");

        when(productRepository.findAll(0, 10, ProductSortField.NAME, SortDirection.ASC))
                .thenReturn(new ProductRepository.ProductPage(List.of(product), 0, 10, 1, 1, true));

        ProductPage result = productService.listProducts(0, 10, "name", SortDirection.ASC);

        assertThat(result.getContent()).hasSize(1);
        assertResponse(result.getContent().get(0), 10L, "USB-C Hub", "HUB-UT-001");
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getLast()).isTrue();
    }

    @Test
    void listProductsRejectsUnsupportedSortProperty() {
        assertThatThrownBy(() -> productService.listProducts(0, 10, "unknownField", SortDirection.ASC))
                .isInstanceOf(ProductException.class)
                .hasMessage("Unsupported product sort property: unknownField");
    }

    @Test
    void getProductReturnsProductById() {
        ProductRow product = product(20L, "Mouse", "MOU-UT-001");

        when(productRepository.findById(20L)).thenReturn(Optional.of(product));

        ProductResponse result = productService.getProduct(20L);

        assertResponse(result, 20L, "Mouse", "MOU-UT-001");
    }

    @Test
    void getProductRejectsMissingProduct() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(404L))
                .isInstanceOf(ProductException.class)
                .hasMessage("Product not found with id: 404");
    }

    @Test
    void updateProductReturnsUpdatedProduct() {
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-UT-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        ProductRow savedProduct = product(30L, "4K Monitor", "MON-UT-001");

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("MON-UT-001", 30L)).thenReturn(false);
        when(productRepository.update(product)).thenReturn(savedProduct);

        ProductResponse result = productService.updateProduct(30L, request, null, 1L);

        assertResponse(result, 30L, "4K Monitor", "MON-UT-001");
        assertThat(product.getName()).isEqualTo("4K Monitor");
        assertThat(product.getSku()).isEqualTo("MON-UT-001");
        assertThat(product.getDescription()).isEqualTo("Updated product");
        assertThat(product.getPrice()).isEqualByComparingTo("199.99");
        assertThat(product.isActive()).isFalse();
    }

    @Test
    void updateProductRejectsDuplicateSku() {
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-UT-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("MON-UT-001", 30L)).thenReturn(true);

        assertThatThrownBy(() -> productService.updateProduct(30L, request, null, 1L))
                .isInstanceOf(ProductException.class)
                .hasMessage("Product SKU already exists: MON-UT-001");
    }

    @Test
    void deleteProductDeletesExistingProduct() {
        ProductRow product = product(40L, "Desk Lamp", "LMP-UT-001");
        when(productRepository.findById(40L)).thenReturn(Optional.of(product));

        productService.deleteProduct(40L, 1L);

        verify(productRepository).delete(product);
    }

    @Test
    void deleteProductRejectsMissingProduct() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(404L, 1L))
                .isInstanceOf(ProductException.class)
                .hasMessage("Product not found with id: 404");
    }

    @Test
    void updateProductThrowsPreconditionFailedOnVersionMismatch() {
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-UT-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        product.setVersion(1L);

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));

        // Mismatched expected version (e.g. 2L vs actual 1L)
        assertThatThrownBy(() -> productService.updateProduct(30L, request, null, 2L))
                .isInstanceOf(com.dibyanshusinha.apiserv.exception.PreconditionFailedException.class)
                .hasMessageContaining("version is 1, but If-Match expected version was 2");
    }

    @Test
    void updateProductSucceedsWithNullIfMatchVersion() {
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-UT-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        ProductRow savedProduct = product(30L, "4K Monitor", "MON-UT-001");

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("MON-UT-001", 30L)).thenReturn(false);
        when(productRepository.update(product)).thenReturn(savedProduct);

        ProductResponse result = productService.updateProduct(30L, request, null, null);

        assertResponse(result, 30L, "4K Monitor", "MON-UT-001");
    }

    @Test
    void updateProductSucceedsWithoutSkuInUpdateMask() {
        // request has duplicate SKU, but updateMask doesn't include sku, so it shouldn't validate or update SKU
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-DUPLICATE-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        ProductRow savedProduct = product(30L, "4K Monitor", "MON-OLD-001"); // SKU remains MON-OLD-001

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        // We mock update here because productRepository.existsBySkuAndIdNot should NOT be called!
        when(productRepository.update(product)).thenReturn(savedProduct);

        ProductResponse result = productService.updateProduct(30L, request, "name, price", null);

        assertResponse(result, 30L, "4K Monitor", "MON-OLD-001");
    }

    @Test
    void updateProductSucceedsWithNullSkuInRequest() {
        ProductUpdateRequest request = updateRequest("4K Monitor", null); // SKU is null in update request
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        ProductRow savedProduct = product(30L, "4K Monitor", "MON-OLD-001");

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.update(product)).thenReturn(savedProduct);

        ProductResponse result = productService.updateProduct(30L, request, null, null);

        assertResponse(result, 30L, "4K Monitor", "MON-OLD-001");
    }

    @Test
    void updateProductSucceedsWithEmptyUpdateMask() {
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-UT-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        ProductRow savedProduct = product(30L, "4K Monitor", "MON-UT-001");

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("MON-UT-001", 30L)).thenReturn(false);
        when(productRepository.update(product)).thenReturn(savedProduct);

        ProductResponse result = productService.updateProduct(30L, request, "   ", null);

        assertResponse(result, 30L, "4K Monitor", "MON-UT-001");
    }

    @Test
    void updateProductSucceedsWithSkuInUpdateMask() {
        ProductUpdateRequest request = updateRequest("4K Monitor", "MON-UT-001");
        ProductRow product = product(30L, "Monitor", "MON-OLD-001");
        ProductRow savedProduct = product(30L, "4K Monitor", "MON-UT-001");

        when(productRepository.findById(30L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuAndIdNot("MON-UT-001", 30L)).thenReturn(false);
        when(productRepository.update(product)).thenReturn(savedProduct);

        ProductResponse result = productService.updateProduct(30L, request, "sku, name", null);

        assertResponse(result, 30L, "4K Monitor", "MON-UT-001");
    }

    @Test
    void deleteProductThrowsPreconditionFailedOnVersionMismatch() {
        ProductRow product = product(40L, "Desk Lamp", "LMP-UT-001");
        product.setVersion(1L);
        when(productRepository.findById(40L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.deleteProduct(40L, 99L))
                .isInstanceOf(com.dibyanshusinha.apiserv.exception.PreconditionFailedException.class)
                .hasMessageContaining("version is 1, but If-Match expected version was 99");
    }

    @Test
    void deleteProductSucceedsWithNullIfMatchVersion() {
        ProductRow product = product(40L, "Desk Lamp", "LMP-UT-001");
        when(productRepository.findById(40L)).thenReturn(Optional.of(product));

        productService.deleteProduct(40L, null);

        verify(productRepository).delete(product);
    }

    private ProductCreateRequest createRequest(String name, String sku) {
        return new ProductCreateRequest(name, sku, BigDecimal.valueOf(99.99))
                .description("Test product")
                .active(true);
    }

    private ProductUpdateRequest updateRequest(String name, String sku) {
        return new ProductUpdateRequest()
                .name(name)
                .sku(sku)
                .price(BigDecimal.valueOf(199.99))
                .active(false)
                .description("Updated product");
    }

    private ProductRow product(Long id, String name, String sku) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        ProductRow product = new ProductRow();
        product.setId(id);
        product.setName(name);
        product.setSku(sku);
        product.setDescription("Test product");
        product.setPrice(BigDecimal.valueOf(99.99));
        product.setActive(true);
        product.setCreatedAt(timestamp);
        product.setUpdatedAt(timestamp);
        product.setVersion(1L);
        return product;
    }

    private void assertResponse(ProductResponse response, Long id, String name, String sku) {
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo(name);
        assertThat(response.getSku()).isEqualTo(sku);
        assertThat(response.getDescription()).isEqualTo("Test product");
        assertThat(response.getPrice()).isEqualByComparingTo("99.99");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getCreatedAt().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(response.getUpdatedAt().toInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }
}
