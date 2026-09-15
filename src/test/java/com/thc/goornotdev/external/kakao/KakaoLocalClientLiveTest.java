package com.thc.goornotdev.external.kakao;

import com.thc.goornotdev.exception.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 카카오 로컬 API 를 호출하는 검증용 테스트.
 *
 * 기본 실행(./gradlew test)에서는 꺼져 있다. TourApiClientLiveTest 와 같은 이유다.
 *   - 외부 네트워크에 의존해 CI 에서 불안정해진다
 *   - 카카오맵 API 무료 쿼터를 매 빌드마다 갉아먹는다
 *
 * 켜는 방법 : ./gradlew test -Dkakao.live=true
 */
@EnabledIfSystemProperty(named = "kakao.live", matches = "true")
@SpringBootTest
class KakaoLocalClientLiveTest {

    @Autowired
    private KakaoLocalClient kakaoLocalClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("시나리오 1 - 시군구명을 좌표로 바꾸고 법정동 코드까지 받아온다")
    void scenario1_geocode() {
        KakaoLocalDto.Coordinate coordinate = kakaoLocalClient.geocode("서울특별시 종로구");

        assertThat(coordinate).isNotNull();
        assertThat(coordinate.getLat()).isNotNull();
        assertThat(coordinate.getLng()).isNotNull();
        // 앞 5자리가 시군구 코드다. 종로구 = 11110
        assertThat(coordinate.sigunguCode()).isEqualTo("11110");

        System.out.println("[시나리오 1] addressName=" + coordinate.getAddressName()
                + ", bCode=" + coordinate.getBCode()
                + ", sigunguCode=" + coordinate.sigunguCode()
                + ", lat=" + coordinate.getLat()
                + ", lng=" + coordinate.getLng());
    }

    /**
     * 시딩에서 가장 위험한 케이스.
     * "중구" 만 던지면 어느 시도의 중구인지 알 수 없으므로, 항상 시도명을 앞에 붙여 질의한다.
     * 두 질의의 bCode 가 실제로 달라야 이 전략이 유효하다.
     */
    @Test
    @DisplayName("시나리오 2 - 동명이의 시군구도 시도명을 붙이면 서로 다른 법정동 코드로 구분된다")
    void scenario2_duplicateSigunguName() {
        KakaoLocalDto.Coordinate seoul = kakaoLocalClient.geocode("서울특별시 중구");
        KakaoLocalDto.Coordinate busan = kakaoLocalClient.geocode("부산광역시 중구");

        assertThat(seoul).isNotNull();
        assertThat(busan).isNotNull();
        assertThat(seoul.sigunguCode()).isNotEqualTo(busan.sigunguCode());
        assertThat(seoul.sigunguCode()).startsWith("11");
        assertThat(busan.sigunguCode()).startsWith("26");

        System.out.println("[시나리오 2] 서울 중구=" + seoul.sigunguCode()
                + " (" + seoul.getLat() + ", " + seoul.getLng() + ")"
                + " / 부산 중구=" + busan.sigunguCode()
                + " (" + busan.getLat() + ", " + busan.getLng() + ")");
    }

    @Test
    @DisplayName("시나리오 3 - 매칭이 없는 주소는 예외가 아니라 null 로 돌아온다")
    void scenario3_noMatch() {
        KakaoLocalDto.Coordinate coordinate = kakaoLocalClient.geocode("존재하지않는시존재하지않는구");

        assertThat(coordinate).isNull();

        System.out.println("[시나리오 3] 매칭 0건 → null 반환 확인");
    }

    @Test
    @DisplayName("시나리오 4 - 잘못된 키는 ExternalApiException 으로 잡히고 키가 평문으로 새지 않는다")
    void scenario4_invalidKey() {
        KakaoLocalClient brokenClient = new KakaoLocalClient(invalidKeyProperties(), objectMapper);

        assertThatThrownBy(() -> brokenClient.geocode("서울특별시 종로구"))
                .isInstanceOf(ExternalApiException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(INVALID_KEY);
                    System.out.println("[시나리오 4] " + e.getMessage());
                });
    }

    private static final String INVALID_KEY = "THIS-IS-AN-INVALID-KAKAO-KEY-FOR-TEST";

    /** KakaoLocalProperties 는 @Value 로만 채워지므로 게터를 덮어써 만든다 */
    private KakaoLocalProperties invalidKeyProperties() {
        return new KakaoLocalProperties() {
            @Override
            public String authorization() {
                return "KakaoAK " + INVALID_KEY;
            }

            @Override
            public String getBaseUrl() {
                return "https://dapi.kakao.com/v2/local";
            }
        };
    }
}
