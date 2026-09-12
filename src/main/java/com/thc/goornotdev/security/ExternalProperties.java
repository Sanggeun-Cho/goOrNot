package com.thc.goornotdev.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class ExternalProperties {
    @Value("${external.jwt.tokenSecretKey}")
    private String tokenSecretKey;

    @Value("${external.jwt.tokenPrefix}")
    private String tokenPrefix;

    @Value("${external.jwt.accessKey}")
    private String accessKey;

    @Value("${external.jwt.accessTokenExpirationTime}")
    private Long accessTokenExpirationTime;

    @Value("${external.jwt.refreshKey}")
    private String refreshKey;

    @Value("${external.jwt.refreshTokenExpirationTime}")
    private Long refreshTokenExpirationTime;

    /**
     * 헤더에 담을 토큰 값 조립 (예: "Bearer eyJhb...")
     */
    public String withPrefix(String token) {
        return tokenPrefix + " " + token;
    }

    /**
     * 헤더 값에서 prefix 를 제거해 순수 토큰만 반환. 형식이 맞지 않으면 null
     */
    public String stripPrefix(String headerValue) {
        if (headerValue == null || !headerValue.startsWith(tokenPrefix)) {
            return null;
        }

        String token = headerValue.substring(tokenPrefix.length()).trim();

        return token.isEmpty() ? null : token;
    }
}
