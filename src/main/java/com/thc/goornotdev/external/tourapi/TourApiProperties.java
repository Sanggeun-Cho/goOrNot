package com.thc.goornotdev.external.tourapi;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TourAPI 4.0 (한국관광공사 국문 관광정보 서비스) 접속 설정.
 *
 * security/ExternalProperties 와 동일하게 @Value 기반으로 application.yml 에서 주입받는다.
 * 인증키는 application.yml(gitignored)에만 두고 코드에 하드코딩하지 않는다.
 */
@Getter
@Component
public class TourApiProperties {
    /**
     * 공공데이터포털에서 발급받은 서비스키.
     * "인코딩(Encoding)" 형태의 값을 그대로 넣는다 (TourApiClient 가 재인코딩하지 않는다).
     */
    @Value("${external.tourapi.service-key}")
    private String serviceKey;

    @Value("${external.tourapi.base-url}")
    private String baseUrl;

    /**
     * 실수로 이 객체를 로그에 찍더라도 인증키가 평문으로 남지 않게 막는다.
     * (Lombok @Getter 는 toString 을 만들지 않지만, 나중에 @ToString 이 붙는 경우까지 대비해 직접 정의)
     */
    @Override
    public String toString() {
        return "TourApiProperties(baseUrl=" + baseUrl + ", serviceKey=****)";
    }
}
