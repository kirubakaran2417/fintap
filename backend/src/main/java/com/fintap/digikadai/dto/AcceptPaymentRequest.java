package com.fintap.digikadai.dto;

import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AcceptPaymentRequest(
        @NotNull @DecimalMin("1.00") BigDecimal amount,
        @NotNull PaymentRail rail,
        String customerLabel,
        String customerMobile,
        String provider
) {
}
