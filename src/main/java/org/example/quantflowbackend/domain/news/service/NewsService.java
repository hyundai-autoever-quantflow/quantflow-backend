package org.example.quantflowbackend.domain.news.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.news.client.NaverNewsClient;
import org.example.quantflowbackend.domain.news.dto.NaverNewsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NewsService {
    private final NaverNewsClient naverNewsClient;

    public NaverNewsResponse search(
            String query,
            int display,
            int start,
            String sort
    ) {
        if (query == null || query.isBlank()) {
            throw badRequest("검색어를 입력해주세요.");
        }

        if (display < 1 || display > 100) {
            throw badRequest("검색 개수는 1~100 사이여야 합니다.");
        }

        if (start < 1 || start > 1000) {
            throw badRequest("검색 시작 위치는 1~1000 사이여야 합니다.");
        }

        if (!"date".equals(sort) && !"sim".equals(sort)) {
            throw badRequest("정렬 방식은 date 또는 sim이어야 합니다.");
        }

        return naverNewsClient.search(
                query.strip(),
                display,
                start,
                sort
        );
    }


    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
