package com.fintap.digikadai.dto;

import java.math.BigDecimal;

public record CustomerProfileDto(
        String token,
        String displayName,
        int visitCount,
        BigDecimal lifetimeSpend,
        double churnRisk
) {
}
