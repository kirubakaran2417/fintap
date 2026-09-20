package com.fintap.digikadai.dto;

import jakarta.validation.constraints.NotBlank;

public record OnboardRequest(
        @NotBlank String shopName,
        @NotBlank String ownerName,
        @NotBlank String category,
        @NotBlank String language,
        String gstin,
        String address,
        String city
) {
}
