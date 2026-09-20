package com.fintap.digikadai.dto;

import com.fintap.digikadai.domain.OndcOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OndcOrderDto(
        Long id,
        String orderRef,
        String buyerApp,
        String itemsSummary,
        BigDecimal amount,
        OndcOrderStatus status,
        Instant createdAt
) {
}
