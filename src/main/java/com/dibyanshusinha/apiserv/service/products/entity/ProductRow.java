package com.dibyanshusinha.apiserv.service.products.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class ProductRow {

    private Long id;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
