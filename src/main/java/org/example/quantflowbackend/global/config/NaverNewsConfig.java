package org.example.quantflowbackend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;

@Configuration
public class NaverNewsConfig {
    @Bean
    public RestClient naverNewsRestClient(
            @Value("${app.naver.client-id}") String clientId,
            @Value("${app.naver.client-secret}") String clientSecret
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(5));

        // NAVER API HUB can return JSON with a text/plain Content-Type.
        JacksonJsonHttpMessageConverter jsonConverter = new JacksonJsonHttpMessageConverter();
        var mediaTypes = new ArrayList<>(jsonConverter.getSupportedMediaTypes());
        mediaTypes.add(MediaType.TEXT_PLAIN);
        jsonConverter.setSupportedMediaTypes(mediaTypes);

        return RestClient.builder()
                .baseUrl("https://naverapihub.apigw.ntruss.com")
                .defaultHeader("X-NCP-APIGW-API-KEY-ID", clientId)
                .defaultHeader("X-NCP-APIGW-API-KEY", clientSecret)
                .requestFactory(factory)
                .configureMessageConverters(converters -> converters.addCustomConverter(jsonConverter))
                .build();
    }
}
