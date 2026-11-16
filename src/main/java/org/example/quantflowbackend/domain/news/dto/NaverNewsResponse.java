package org.example.quantflowbackend.domain.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverNewsResponse(
        String lastBuildDate,
        long total,
        int start,
        int display,
        List<Item> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String originallink,
            String link,
            String description,
            String pubDate
    ) {

    }
}
