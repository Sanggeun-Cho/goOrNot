package com.thc.goornotdev.external.kakao;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 카카오 로컬 API 설정.
 *
 * security/ExternalProperties 와 같은 @Value 기반 패턴.
 * REST API 키는 application.yml(gitignored)에만 두고 코드에 하드코딩하지 않는다.
 */
@Getter
@Component
public class KakaoLocalProperties {
    @Value("${external.kakao.rest-api-key}")
    private String restApiKey;

    @Value("${external.kakao.local-base-url:https://dapi.kakao.com/v2/local}")
    private String baseUrl;

    /**
     * 인증 헤더 값. 카카오는 "Authorization: KakaoAK {REST_API_KEY}" 형식을 쓴다.
     */
    public String authorization() {
        return "KakaoAK " + restApiKey;
    }

    /**
     * 로그에 키가 찍히지 않도록 가린다.
     * TourApiProperties 와 같은 이유로 toString() 을 직접 막아둔다.
     */
    @Override
    public String toString() {
        return "KakaoLocalProperties(baseUrl=" + baseUrl + ", restApiKey=****)";
    }
}
