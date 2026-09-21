package com.fintap.digikadai.dto;

import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDto(
        Long id,
        BigDecimal amount,
        PaymentRail rail,
        TransactionStatus status,
        String customerLabel,
        String customerMobile,
        String reference,
        Instant createdAt,
        String checkoutUrl,
        String gatewayOrderId,
        String gatewaySessionId,
        String gatewayProvider,
        String failureReason
) {
}
