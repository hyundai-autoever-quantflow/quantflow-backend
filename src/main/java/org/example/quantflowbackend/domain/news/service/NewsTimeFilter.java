package org.example.quantflowbackend.domain.news.service;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Component
public class NewsTimeFilter {
    public Optional<OffsetDateTime> parse(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    OffsetDateTime.parse(
                            pubDate.strip(),
                            DateTimeFormatter.RFC_1123_DATE_TIME
                    )
            );
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    public boolean isWithin(
            OffsetDateTime providedAt,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        return !providedAt.isBefore(from)
                && providedAt.isBefore(to);
    }
}
