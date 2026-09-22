package com.fintap.digikadai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record KhataDto(
        Long id,
        String customerName,
        String mobile,
        BigDecimal amount,
        boolean credit,
        String note,
        Instant createdAt
) {
    public record CreateRequest(
            @NotBlank String customerName,
            String mobile,
            @NotNull @DecimalMin("1.00") BigDecimal amount,
            boolean credit,
            String note,
            String entryDate
    ) {
        public CreateRequest(String customerName, String mobile, BigDecimal amount, boolean credit, String note) {
            this(customerName, mobile, amount, credit, note, null);
        }
    }
}
