package com.dibyanshusinha.apiserv.service.products.exception;

import com.dibyanshusinha.apiserv.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

public class ProductException extends ApiException {

    private ProductException(@NonNull HttpStatus status, String title, String message) {
        super(status, title, message);
    }

    public static ProductException notFound(Long id) {
        return new ProductException(
                HttpStatus.NOT_FOUND,
                "Product not found",
                "Product not found with id: " + id);
    }

    public static ProductException duplicateSku(String sku) {
        return new ProductException(
                HttpStatus.CONFLICT,
                "Duplicate SKU",
                "Product SKU already exists: " + sku);
    }

    public static ProductException invalidSortProperty(String sortBy) {
        return new ProductException(
                HttpStatus.BAD_REQUEST,
                "Invalid sort property",
                "Unsupported product sort property: " + sortBy);
    }
}
