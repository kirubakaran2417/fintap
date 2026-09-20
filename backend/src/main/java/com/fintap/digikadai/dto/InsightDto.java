package com.fintap.digikadai.dto;

import java.time.Instant;

public record InsightDto(
        Long id,
        String title,
        String body,
        String type,
        Instant createdAt
) {
}
