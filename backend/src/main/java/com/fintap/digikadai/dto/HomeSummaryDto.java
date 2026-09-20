package com.fintap.digikadai.dto;

import java.math.BigDecimal;

public record HomeSummaryDto(
        BigDecimal todayRevenue,
        long todayCustomers,
        BigDecimal ondcGmv,
        BigDecimal khataOutstanding,
        String routingNudge,
        java.util.List<PaymentDto> recentPayments
) {
}
