package org.example.quantflowbackend.domain.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.news.service.NewsPreparationService;
import org.example.quantflowbackend.domain.news.service.NewsService;
import org.example.quantflowbackend.domain.recommendation.dto.SearchedNewsAnalysisRequest;
import org.example.quantflowbackend.domain.recommendation.dto.SearchedNewsAnalysisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SearchedNewsAnalysisService {
    private final NewsService newsService;
    private final NewsPreparationService newsPreparationService;
    private final NewsAnalysisService newsAnalysisService;

    public SearchedNewsAnalysisResponse analyze(
            SearchedNewsAnalysisRequest request
    ) {
        if (request.from() == null
                || request.to() == null
                || !request.from().isBefore(request.to())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "시작 시각은 종료 시각보다 이전이어야 합니다."
            );
        }

        var searchResult = newsService.search(
                request.query(),
                100,
                1,
                "date"
        );

        var preparedNews = newsPreparationService.prepare(
                searchResult.items(),
                request.from(),
                request.to()
        );

        String analysis = newsAnalysisService.analyze(preparedNews);

        return new SearchedNewsAnalysisResponse(
                request.query().strip(),
                searchResult.items().size(),
                preparedNews,
                analysis
        );
    }
}
