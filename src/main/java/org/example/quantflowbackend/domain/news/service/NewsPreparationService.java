package org.example.quantflowbackend.domain.news.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.news.dto.NaverNewsResponse;
import org.example.quantflowbackend.domain.news.dto.PreparedNews;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NewsPreparationService {
    private final NewsTextCleaner newsTextCleaner;
    private final NewsTimeFilter newsTimeFilter;

    public List<PreparedNews> prepare(
            List<NaverNewsResponse.Item> items,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        validatePeriod(from, to);

        Set<String> seenUrls = new HashSet<>();
        List<PreparedNews> preparedNews = new ArrayList<>();

        for (NaverNewsResponse.Item item : items) {
            if (item == null) {
                continue;
            }

            Optional<OffsetDateTime> parsedDate = newsTimeFilter.parse(item.pubDate());

            if (parsedDate.isEmpty()) {
                continue;
            }

            OffsetDateTime providedAt = parsedDate.get();

            if (!newsTimeFilter.isWithin(providedAt, from, to)) {
                continue;
            }

            String sourceUrl = selectSourceUrl(item);

            if (sourceUrl.isBlank()) {
                continue;
            }

            String title = newsTextCleaner.clean(item.title());
            String description = newsTextCleaner.clean(item.description());

            if (title.isBlank() || description.isBlank()) {
                continue;
            }

            if (!seenUrls.add(sourceUrl)) {
                continue;
            }

            preparedNews.add(new PreparedNews(
                    title,
                    description,
                    sourceUrl,
                    providedAt
            ));
        }

        return List.copyOf(preparedNews);
    }

    private String selectSourceUrl(NaverNewsResponse.Item item) {
        if (item.originallink() != null
                && !item.originallink().isBlank()) {
            return item.originallink().strip();
        }

        return item.link() == null ? "" : item.link().strip();
    }

    private void validatePeriod(
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시작 시각은 종료 시각보다 이전이어야 합니다."
            );
        }
    }
}
