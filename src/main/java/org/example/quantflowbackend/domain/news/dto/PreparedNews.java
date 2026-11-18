package org.example.quantflowbackend.domain.news.dto;

import java.time.OffsetDateTime;

public record PreparedNews(
        String title,
        String description,
        String sourceUrl,
        OffsetDateTime providedAt
) {
}
