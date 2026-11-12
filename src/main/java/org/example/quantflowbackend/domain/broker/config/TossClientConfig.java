package org.example.quantflowbackend.domain.broker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class TossClientConfig {
    @Bean
    public RestClient tossRestClient(
            @Value("${app.broker.toss.base-url:https://openapi.tossinvest.com}") String baseUrl,
            @Value("${app.broker.toss.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.broker.toss.read-timeout:5s}") Duration readTimeout) {
        if (connectTimeout.isNegative() || connectTimeout.isZero() || connectTimeout.toSeconds() > 5
                || readTimeout.isNegative() || readTimeout.isZero() || readTimeout.toSeconds() > 10) {
            throw new IllegalArgumentException("토스 연결 제한시간은 5초, 응답 제한시간은 10초 이하여야 합니다.");
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
