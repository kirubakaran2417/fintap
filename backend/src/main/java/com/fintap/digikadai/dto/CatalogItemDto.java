package com.fintap.digikadai.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CatalogItemDto(
        Long id,
        String name,
        String barcode,
        String category,
        BigDecimal mrp,
        BigDecimal sellingPrice,
        int stock,
        boolean publishedToOndc,
        String description
) {
    public record CreateRequest(
            @NotBlank String name,
            String barcode,
            String category,
            BigDecimal mrp,
            BigDecimal sellingPrice,
            int stock,
            String description
    ) {
    }
}
