package com.fintap.digikadai.dto;

public record AuthResponse(
        String token,
        Long merchantId,
        boolean onboarded,
        String shopName,
        String language
) {
}
