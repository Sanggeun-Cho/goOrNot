package com.thc.goornotdev.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정.
 *
 * springdoc 의 기본값이 enabled=true 라서 matchIfMissing=true 로 맞춰둔다.
 * 그래야 yml 에 키가 없으면 둘 다 켜지고, 배포용 yml 에 springdoc.api-docs.enabled=false 를
 * 넣으면 문서 엔드포인트와 이 설정이 함께 꺼진다.
 * havingValue="true" 만 걸면 이 빈만 빠지고 /v3/api-docs 는 살아 있어서 오히려 위험하다.
 */
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
public class SwaggerConfig {

    /** 대부분의 API 가 쓰는 Access Token */
    private static final String ACCESS = "Access Token";

    /**
     * Refresh Token 스킴은 일부러 두지 않는다.
     * 회원가입·로그인·재발급 플로우는 Swagger 가 아니라 테스트 프론트에서 확인한다.
     * Swagger 는 이미 발급받은 Access Token 을 붙여 개별 API 를 두드려보는 용도다.
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                // Authorization: Bearer {token}
                .components(new Components()
                        .addSecuritySchemes(ACCESS, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인 응답 헤더로 받은 Access Token")))
                .addSecurityItem(new SecurityRequirement().addList(ACCESS));
    }

    private Info apiInfo() {
        return new Info()
                .title("갈래 말래")
                .description("관광데이터 활용 공모전 '갈래 말래' API 문서")
                .version("1.0.0");
    }
}
