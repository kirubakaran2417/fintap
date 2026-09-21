package com.fintap.digikadai.dto;

import java.math.BigDecimal;
import java.util.List;

public record HomeSummaryDto(
        BigDecimal todayRevenue,
        long todayCustomers,
        BigDecimal weekRevenue,
        BigDecimal monthRevenue,
        BigDecimal cardToday,
        BigDecimal upiToday,
        long pendingPayments,
        BigDecimal ondcGmv,
        long ondcOpenOrders,
        long catalogPublished,
        long catalogTotal,
        BigDecimal khataOutstanding,
        String routingNudge,
        List<DayPoint> last7Days,
        List<PaymentDto> recentPayments
) {
    public record DayPoint(String label, BigDecimal amount, long count) {
    }
}
