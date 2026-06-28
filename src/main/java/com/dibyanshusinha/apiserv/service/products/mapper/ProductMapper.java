package com.dibyanshusinha.apiserv.service.products.mapper;

import com.dibyanshusinha.apiserv.generated.model.ProductCreateRequest;
import com.dibyanshusinha.apiserv.generated.model.ProductResponse;
import com.dibyanshusinha.apiserv.generated.model.ProductUpdateRequest;
import com.dibyanshusinha.apiserv.service.products.entity.ProductRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", expression = "java(defaultActive(request.getActive()))")
    ProductRow toRow(ProductCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateRow(@MappingTarget ProductRow product, ProductUpdateRequest request);

    @Mapping(target = "createdAt", source = "createdAt", qualifiedByName = "toOffsetDateTime")
    @Mapping(target = "updatedAt", source = "updatedAt", qualifiedByName = "toOffsetDateTime")
    ProductResponse toResponse(ProductRow product);

    default boolean defaultActive(Boolean active) {
        return active == null || active;
    }

    @Named("toOffsetDateTime")
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
    }

    default void updateRowSelective(ProductRow product, ProductUpdateRequest request, String updateMask) {
        if (updateMask == null || updateMask.trim().isEmpty()) {
            if (request.getName() != null) product.setName(request.getName());
            if (request.getSku() != null) product.setSku(request.getSku());
            if (request.getDescription() != null) product.setDescription(request.getDescription());
            if (request.getPrice() != null) product.setPrice(request.getPrice());
            if (request.getActive() != null) product.setActive(request.getActive());
            return;
        }

        java.util.Set<String> fields = java.util.Arrays.stream(updateMask.split(","))
                .map(s -> s.trim())
                .collect(java.util.stream.Collectors.toSet());

        if (fields.contains("name")) product.setName(request.getName());
        if (fields.contains("sku")) product.setSku(request.getSku());
        if (fields.contains("description")) product.setDescription(request.getDescription());
        if (fields.contains("price")) product.setPrice(request.getPrice());
        if (fields.contains("active")) {
            if (request.getActive() != null) {
                product.setActive(request.getActive());
            }
        }
    }
}
