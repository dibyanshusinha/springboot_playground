package com.dibyanshusinha.apiserv.service.products.controller;

import com.dibyanshusinha.apiserv.generated.api.ProductsApi;
import com.dibyanshusinha.apiserv.generated.model.ProductCreateRequest;
import com.dibyanshusinha.apiserv.generated.model.ProductPage;
import com.dibyanshusinha.apiserv.generated.model.ProductResponse;
import com.dibyanshusinha.apiserv.generated.model.ProductUpdateRequest;
import com.dibyanshusinha.apiserv.generated.model.SortDirection;
import com.dibyanshusinha.apiserv.service.products.ProductService;
import com.dibyanshusinha.apiserv.service.products.util.ProductConstants;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class ProductController implements ProductsApi {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    @com.dibyanshusinha.apiserv.observability.Idempotent
    public ResponseEntity<ProductResponse> createProduct(ProductCreateRequest productCreateRequest, String xCorrelationId, java.util.UUID idempotencyKey) {
        ProductResponse response = productService.createProduct(productCreateRequest);
        return ResponseEntity
                .created(java.util.Objects.requireNonNull(URI.create(ProductConstants.API_PATH_WITH_TRAILING_SLASH + response.getId())))
                .eTag(String.valueOf(response.getVersion()))
                .body(response);
    }

    @Override
    public ResponseEntity<ProductPage> listProducts(String xCorrelationId, Integer pageSize, String pageToken, String sortBy, SortDirection direction) {
        int size = (pageSize != null) ? pageSize : 20;
        int page = 0;
        if (pageToken != null && !pageToken.trim().isEmpty()) {
            try {
                String decoded = pageToken;
                try {
                    decoded = new String(java.util.Base64.getDecoder().decode(pageToken.trim()), java.nio.charset.StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    // Ignore, maybe it's not base64 encoded
                }
                
                if (decoded.startsWith("page:")) {
                    page = Integer.parseInt(decoded.substring(5));
                } else {
                    page = Integer.parseInt(decoded.trim());
                }
            } catch (NumberFormatException e) {
                page = 0;
            }
        }
        
        ProductPage productPage = productService.listProducts(page, size, sortBy, direction);
        
        if (productPage.getLast() != null && !productPage.getLast()) {
            int nextPage = page + 1;
            String tokenValue = "page:" + nextPage;
            String encodedToken = java.util.Base64.getEncoder().encodeToString(tokenValue.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            productPage.setNextPageToken(encodedToken);
        } else {
            productPage.setNextPageToken(null);
        }
        
        return ResponseEntity.ok(productPage);
    }

    @Override
    public ResponseEntity<ProductResponse> getProduct(Long productId, String xCorrelationId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok()
                .eTag(String.valueOf(response.getVersion()))
                .body(response);
    }

    @Override
    public ResponseEntity<ProductResponse> updateProduct(Long productId, ProductUpdateRequest productUpdateRequest, String xCorrelationId, String updateMask, String ifMatch) {
        Long expectedVersion = parseETag(ifMatch);
        ProductResponse response = productService.updateProduct(productId, productUpdateRequest, updateMask, expectedVersion);
        return ResponseEntity.ok()
                .eTag(String.valueOf(response.getVersion()))
                .body(response);
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long productId, String xCorrelationId, String ifMatch) {
        Long expectedVersion = parseETag(ifMatch);
        productService.deleteProduct(productId, expectedVersion);
        return ResponseEntity.noContent().build();
    }

    private Long parseETag(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String clean = ifMatch.trim();
        if (clean.startsWith("W/")) {
            clean = clean.substring(2);
        }
        if (clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            throw new com.dibyanshusinha.apiserv.exception.PreconditionFailedException("Invalid ETag format in If-Match header: " + ifMatch);
        }
    }
}
