package com.fintap.digikadai.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String mobile,
        @NotBlank String pin
) {
}
