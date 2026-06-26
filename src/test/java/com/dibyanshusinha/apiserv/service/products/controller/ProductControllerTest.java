package com.dibyanshusinha.apiserv.service.products.controller;

import com.dibyanshusinha.apiserv.generated.model.ProductCreateRequest;
import com.dibyanshusinha.apiserv.generated.model.ProductPage;
import com.dibyanshusinha.apiserv.generated.model.ProductResponse;
import com.dibyanshusinha.apiserv.generated.model.ProductUpdateRequest;
import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.ProductService;
import com.dibyanshusinha.apiserv.service.products.util.ProductConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    @Test
    void createProductReturnsCreatedLocation() {
        ProductCreateRequest request = new ProductCreateRequest("Keyboard", "KEY-CTRL-001", BigDecimal.TEN)
                .description("Test")
                .active(true);
        ProductResponse response = response(99L, "Keyboard", "KEY-CTRL-001");
        when(productService.createProduct(request)).thenReturn(response);

        ResponseEntity<ProductResponse> result = productController.createProduct(request, "test-correlation-id", null);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getLocation()).hasToString(ProductConstants.API_PATH_WITH_TRAILING_SLASH + "99");
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void listProductsDelegatesPagingAndSorting() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 10, 1L, 1, true);
        when(productService.listProducts(0, 10, "name", SortDirection.ASC)).thenReturn(page);

        String token = java.util.Base64.getEncoder().encodeToString("page:0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<ProductPage> result = productController.listProducts("test-correlation-id", 10, token, "name", SortDirection.ASC);

        assertThat(result.getBody()).isEqualTo(page);
    }

    @Test
    void listProductsWithNullPageSizeDefaultsTo20() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 20, 1L, 1, true);
        when(productService.listProducts(0, 20, "name", SortDirection.ASC)).thenReturn(page);

        ResponseEntity<ProductPage> result = productController.listProducts("test-correlation-id", null, null, "name", SortDirection.ASC);

        assertThat(result.getBody().getSize()).isEqualTo(20);
        assertThat(result.getBody().getNextPageToken()).isNull();
    }

    @Test
    void listProductsWithNullOrEmptyPageTokenDefaultsToPage0() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 10, 1L, 1, true);
        when(productService.listProducts(0, 10, "name", SortDirection.ASC)).thenReturn(page);

        ResponseEntity<ProductPage> result1 = productController.listProducts("test-correlation-id", 10, null, "name", SortDirection.ASC);
        ResponseEntity<ProductPage> result2 = productController.listProducts("test-correlation-id", 10, "   ", "name", SortDirection.ASC);

        assertThat(result1.getBody().getPage()).isZero();
        assertThat(result2.getBody().getPage()).isZero();
    }

    @Test
    void listProductsWithIntegerPageTokenDecodesCorrectly() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 2, 10, 25L, 3, false);
        // Base64 of "2" is "Mg=="
        String token = java.util.Base64.getEncoder().encodeToString("2".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(productService.listProducts(2, 10, "name", SortDirection.ASC)).thenReturn(page);

        ResponseEntity<ProductPage> result = productController.listProducts("test-correlation-id", 10, token, "name", SortDirection.ASC);

        assertThat(result.getBody().getPage()).isEqualTo(2);
    }

    @Test
    void listProductsWithInvalidBase64PageTokenDefaultsToPage0() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 10, 1L, 1, true);
        when(productService.listProducts(0, 10, "name", SortDirection.ASC)).thenReturn(page);

        // "not-base-64!" is not valid base64
        ResponseEntity<ProductPage> result = productController.listProducts("test-correlation-id", 10, "not-base-64!", "name", SortDirection.ASC);

        assertThat(result.getBody().getPage()).isZero();
    }

    @Test
    void listProductsWithNonNumericPageTokenDefaultsToPage0() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 10, 1L, 1, true);
        when(productService.listProducts(0, 10, "name", SortDirection.ASC)).thenReturn(page);

        // Base64 of "page:abc"
        String token = java.util.Base64.getEncoder().encodeToString("page:abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<ProductPage> result = productController.listProducts("test-correlation-id", 10, token, "name", SortDirection.ASC);

        assertThat(result.getBody().getPage()).isZero();
    }

    @Test
    void listProductsWithLastFalseGeneratesNextPageToken() {
        ProductPage page = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 1, 10, 25L, 3, false);
        when(productService.listProducts(1, 10, "name", SortDirection.ASC)).thenReturn(page);

        String token = java.util.Base64.getEncoder().encodeToString("page:1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<ProductPage> result = productController.listProducts("test-correlation-id", 10, token, "name", SortDirection.ASC);

        // Expected next page token is base64 of "page:2", which is "cGFnZToy"
        assertThat(result.getBody().getNextPageToken()).isEqualTo("cGFnZToy");
    }

    @Test
    void listProductsWithLastNullOrTrueSetsNextPageTokenToNull() {
        ProductPage page1 = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 10, 1L, 1, true);
        ProductPage page2 = new ProductPage(List.of(response(1L, "Keyboard", "KEY-CTRL-001")), 0, 10, 1L, 1, null);
        
        when(productService.listProducts(0, 10, "name", SortDirection.ASC)).thenReturn(page1).thenReturn(page2);

        ResponseEntity<ProductPage> result1 = productController.listProducts("test-correlation-id", 10, null, "name", SortDirection.ASC);
        ResponseEntity<ProductPage> result2 = productController.listProducts("test-correlation-id", 10, null, "name", SortDirection.ASC);

        assertThat(result1.getBody().getNextPageToken()).isNull();
        assertThat(result2.getBody().getNextPageToken()).isNull();
    }

    @Test
    void updateProductWithWeakETagParsesCorrectly() {
        ProductUpdateRequest request = new ProductUpdateRequest().name("Monitor");
        ProductResponse response = response(10L, "Monitor", "MON-CTRL-001");
        when(productService.updateProduct(10L, request, null, 15L)).thenReturn(response);

        ResponseEntity<ProductResponse> result = productController.updateProduct(10L, request, "test-correlation-id", null, "W/\"15\"");

        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void updateProductWithQuotedETagParsesCorrectly() {
        ProductUpdateRequest request = new ProductUpdateRequest().name("Monitor");
        ProductResponse response = response(10L, "Monitor", "MON-CTRL-001");
        when(productService.updateProduct(10L, request, null, 42L)).thenReturn(response);

        ResponseEntity<ProductResponse> result = productController.updateProduct(10L, request, "test-correlation-id", null, "\"42\"");

        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void updateProductWithInvalidETagThrowsPreconditionFailed() {
        ProductUpdateRequest request = new ProductUpdateRequest().name("Monitor");

        assertThatThrownBy(() -> productController.updateProduct(10L, request, "test-correlation-id", null, "not-a-number"))
                .isInstanceOf(com.dibyanshusinha.apiserv.exception.PreconditionFailedException.class)
                .hasMessageContaining("Invalid ETag format");
    }

    @Test
    void updateProductWithBlankETagParsesAsNull() {
        ProductUpdateRequest request = new ProductUpdateRequest().name("Monitor");
        ProductResponse response = response(10L, "Monitor", "MON-CTRL-001");
        when(productService.updateProduct(10L, request, null, null)).thenReturn(response);

        ResponseEntity<ProductResponse> result1 = productController.updateProduct(10L, request, "test-correlation-id", null, null);
        ResponseEntity<ProductResponse> result2 = productController.updateProduct(10L, request, "test-correlation-id", null, "   ");

        assertThat(result1.getBody()).isEqualTo(response);
        assertThat(result2.getBody()).isEqualTo(response);
    }

    @Test
    void updateProductWithWeakETagNoQuotesParsesCorrectly() {
        ProductUpdateRequest request = new ProductUpdateRequest().name("Monitor");
        ProductResponse response = response(10L, "Monitor", "MON-CTRL-001");
        when(productService.updateProduct(10L, request, null, 15L)).thenReturn(response);

        ResponseEntity<ProductResponse> result = productController.updateProduct(10L, request, "test-correlation-id", null, "W/15");

        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void updateProductWithOneQuoteETagDoesNotStripAndParsesCorrectlyOrFails() {
        ProductUpdateRequest request = new ProductUpdateRequest().name("Monitor");
        
        // ETag starting with but not ending with quote: "15 -> invalid format (NumberFormatException)
        assertThatThrownBy(() -> productController.updateProduct(10L, request, "test-correlation-id", null, "\"15"))
                .isInstanceOf(com.dibyanshusinha.apiserv.exception.PreconditionFailedException.class);

        // ETag ending with but not starting with quote: 15" -> invalid format (NumberFormatException)
        assertThatThrownBy(() -> productController.updateProduct(10L, request, "test-correlation-id", null, "15\""))
                .isInstanceOf(com.dibyanshusinha.apiserv.exception.PreconditionFailedException.class);
    }

    @Test
    void getProductDelegatesToService() {
        ProductResponse response = response(10L, "Mouse", "MOU-CTRL-001");
        when(productService.getProduct(10L)).thenReturn(response);

        ResponseEntity<ProductResponse> result = productController.getProduct(10L, "test-correlation-id");

        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void updateProductDelegatesToService() {
        ProductUpdateRequest request = new ProductUpdateRequest()
                .name("Monitor")
                .sku("MON-CTRL-001")
                .price(BigDecimal.valueOf(99))
                .active(false)
                .description("Updated");
        ProductResponse response = response(10L, "Monitor", "MON-CTRL-001");
        when(productService.updateProduct(10L, request, null, null)).thenReturn(response);

        ResponseEntity<ProductResponse> result = productController.updateProduct(10L, request, "test-correlation-id", null, null);

        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void deleteProductDelegatesToService() {
        ResponseEntity<Void> result = productController.deleteProduct(10L, "test-correlation-id", null);

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        verify(productService).deleteProduct(10L, null);
    }

    private ProductResponse response(Long id, String name, String sku) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        return new ProductResponse(id, name, sku, BigDecimal.valueOf(99), true, timestamp, timestamp)
                .description("Test product")
                .version(1L);
    }
}
