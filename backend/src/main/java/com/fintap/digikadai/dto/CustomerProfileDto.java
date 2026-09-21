package com.fintap.digikadai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CustomerProfileDto(
        String token,
        String displayName,
        int visitCount,
        BigDecimal lifetimeSpend,
        double churnRisk,
        String source
) {
    public record CreateRequest(
            @NotBlank String displayName,
            @DecimalMin("1.00") BigDecimal amount
    ) {
    }
}
