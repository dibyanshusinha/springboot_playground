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
import com.dibyanshusinha.apiserv.exception.PreconditionFailedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw ProductException.duplicateSku(request.getSku());
        }

        ProductRow product = productMapper.toRow(request);
        return productMapper.toResponse(productRepository.create(product));
    }

    @Transactional(readOnly = true)
    public ProductPage listProducts(int page, int size, String sortBy, SortDirection direction) {
        ProductSortField sortField = ProductSortField.fromProperty(sortBy)
                .orElseThrow(() -> ProductException.invalidSortProperty(sortBy));

        ProductRepository.ProductPage products = productRepository.findAll(page, size, sortField, direction);
        List<ProductResponse> responses = products.content().stream()
                .map(productMapper::toResponse)
                .toList();
        return new ProductPage()
                .content(responses)
                .page(products.page())
                .size(products.size())
                .totalElements(products.totalElements())
                .totalPages(products.totalPages())
                .last(products.last());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        return productMapper.toResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request, String updateMask, Long ifMatchVersion) {
        ProductRow product = findProduct(id);

        if (ifMatchVersion != null && !ifMatchVersion.equals(product.getVersion())) {
            throw new PreconditionFailedException("Product with ID " + id + " version is " + product.getVersion() + ", but If-Match expected version was " + ifMatchVersion);
        }

        // Only validate SKU uniqueness if SKU is being updated
        boolean isSkuUpdated = (updateMask == null || updateMask.trim().isEmpty() || updateMask.contains("sku"));
        if (isSkuUpdated && request.getSku() != null && productRepository.existsBySkuAndIdNot(request.getSku(), id)) {
            throw ProductException.duplicateSku(request.getSku());
        }

        productMapper.updateRowSelective(product, request, updateMask);
        return productMapper.toResponse(productRepository.update(product));
    }

    @Transactional
    public void deleteProduct(Long id, Long ifMatchVersion) {
        ProductRow product = findProduct(id);
        if (ifMatchVersion != null && !ifMatchVersion.equals(product.getVersion())) {
            throw new PreconditionFailedException("Product with ID " + id + " version is " + product.getVersion() + ", but If-Match expected version was " + ifMatchVersion);
        }
        productRepository.delete(product);
    }

    private ProductRow findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> ProductException.notFound(id));
    }
}
