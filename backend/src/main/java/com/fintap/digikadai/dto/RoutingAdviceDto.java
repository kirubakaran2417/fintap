package com.fintap.digikadai.dto;

import com.fintap.digikadai.domain.PaymentRail;

import java.math.BigDecimal;

public record RoutingAdviceDto(
        PaymentRail recommendedRail,
        String reason,
        BigDecimal estimatedMdr,
        boolean profitableForMerchant
) {
}
