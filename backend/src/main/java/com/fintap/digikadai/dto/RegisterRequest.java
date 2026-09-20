package com.fintap.digikadai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String mobile,
        @NotBlank @Size(min = 4, max = 6) String pin,
        @NotBlank String ownerName
) {
}
