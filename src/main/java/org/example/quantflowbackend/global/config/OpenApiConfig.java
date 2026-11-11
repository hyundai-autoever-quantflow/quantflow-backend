package org.example.quantflowbackend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QUANTFLOW API")
                        .description("""
                                Android 앱용 개인화 뉴스 분석 및 자동매매 API입니다.

                                인증 순서:
                                1. Android 앱에서 Google SDK로 받은 ID 토큰을 POST /api/v1/auth/oauth2에 전달합니다.
                                2. 응답 body의 Access Token을 Swagger의 Authorize에 입력합니다.
                                3. 일반 API는 서비스 Access Token으로 호출합니다.
                                4. 만료 시 POST /api/v1/auth/reissue에 Refresh Token을 body로 전달하고 두 토큰을 교체합니다.

                                인증 API 3종은 Access Token 없이 호출합니다. Google ID 토큰과 Refresh Token은 일반 API 인증용이 아닙니다.
                                """)
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("로그인 또는 재발급 응답의 서비스 Access Token 값만 입력하세요. Bearer 접두사는 자동으로 추가됩니다.")))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                );
    }

    @Bean
    public OpenApiCustomizer publicAuthOperations() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (String path : List.of(
                    "/api/v1/auth/oauth2",
                    "/api/v1/auth/reissue",
                    "/api/v1/auth/logout"
            )) {
                var item = openApi.getPaths().get(path);
                if (item != null && item.getPost() != null) {
                    item.getPost().setSecurity(List.of());
                }
            }
        };
    }
}
