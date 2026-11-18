package org.example.quantflowbackend.domain.news.client;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.news.dto.NaverNewsResponse;
import org.example.quantflowbackend.global.config.NaverNewsConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NaverNewsClient {
    private final RestClient restClient;

    public NaverNewsClient(
            @Qualifier("naverNewsRestClient") RestClient restClient
    ) {
        this.restClient = restClient;
    }

    public NaverNewsResponse search(
            String query,
            int display,
            int start,
            String sort
    ) {
        try {
            NaverNewsResponse response = restClient.get()
                    .uri(
                            "/search/v1/news"
                                    + "?query={query}"
                                    + "&display={display}"
                                    + "&start={start}"
                                    + "&sort={sort}"
                                    + "&format=json",
                            query,
                            display,
                            start,
                            sort
                    )
                    .retrieve()
                    .body(NaverNewsResponse.class);

            if (response == null || response.items() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "네이버 뉴스 응답 형식이 올바르지 않습니다."
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "네이버 뉴스 API에 연결할 수 없습니다."
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "네이버 뉴스 응답을 처리할 수 없습니다."
            );
        }


    }

    private ResponseStatusException translate(
            RestClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();

        if (status == 400) {
            return new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "뉴스 검색 조건을 확인해주세요."
            );
        }

        if (status == 401 || status == 403) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "네이버 API 인증정보와 검색 API 사용 권한을 확인해주세요."
            );
        }

        if (status == 429) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "네이버 API 요청 한도를 초과했습니다."
            );
        }

        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "네이버 뉴스 검색을 처리할 수 없습니다."
        );
    }

}
