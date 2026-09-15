package com.thc.goornotdev.external.kakao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 네트워크 없이 도는 카카오 클라이언트 테스트.
 *
 * 카카오는 키를 Authorization 헤더로 보내기 때문에 URI 에는 키가 섞이지 않지만,
 * 하위 예외 메시지에 헤더가 딸려 나올 수 있어 마스킹이 마지막 방어선이다.
 */
class KakaoLocalClientTest {

    @Test
    @DisplayName("마스킹 - KakaoAK 뒤의 키 값이 로그·예외 메시지에 남지 않는다")
    void mask_hidesKey() {
        String masked = KakaoLocalClient.mask(
                "401 Unauthorized: header Authorization=KakaoAK abcdef0123456789abcdef0123456789");

        assertThat(masked).doesNotContain("abcdef0123456789");
        assertThat(masked).contains("KakaoAK ****");
    }

    @Test
    @DisplayName("마스킹 - 대소문자가 달라도 가린다")
    void mask_ignoresCase() {
        assertThat(KakaoLocalClient.mask("authorization: kakaoak SECRETKEYVALUE"))
                .doesNotContain("SECRETKEYVALUE");
    }

    @Test
    @DisplayName("마스킹 - null 은 그대로 null")
    void mask_null() {
        assertThat(KakaoLocalClient.mask(null)).isNull();
    }

    /**
     * 실제 라이브 테스트에서 잡힌 유출 경로의 회귀 방지.
     *
     * 키가 틀리면 카카오가 401 본문에 우리가 보낸 키를 그대로 되돌려준다.
     * 이 본문을 예외 메시지에 붙이면 키가 로그와 502 응답으로 새어 나간다.
     */
    @Test
    @DisplayName("마스킹 - 카카오가 오류 본문에 되돌려준 appKey 값도 가린다")
    void mask_hidesEchoedAppKey() {
        String masked = KakaoLocalClient.mask(
                "{\"errorType\":\"AccessDeniedError\","
                        + "\"message\":\"wrong appKey(abcdef0123456789abcdef0123456789) format\"}");

        assertThat(masked).doesNotContain("abcdef0123456789");
        assertThat(masked).contains("appKey(****)");
        // 원인 파악에 필요한 정보는 남아 있어야 한다
        assertThat(masked).contains("AccessDeniedError");
    }

    @Test
    @DisplayName("마스킹 - appKey 가 비어 있어도 깨지지 않는다")
    void mask_emptyAppKey() {
        assertThat(KakaoLocalClient.mask("wrong appKey() format")).contains("appKey(****)");
    }

    @Test
    @DisplayName("시군구 코드 - 법정동 10자리에서 앞 5자리만 떼어낸다")
    void sigunguCode() {
        KakaoLocalDto.Coordinate coordinate = KakaoLocalDto.Coordinate.builder()
                .bCode("1111000000")
                .build();

        assertThat(coordinate.sigunguCode()).isEqualTo("11110");
    }

    @Test
    @DisplayName("시군구 코드 - 값이 없거나 짧으면 null 로 떨어져 시딩에서 걸러진다")
    void sigunguCode_invalid() {
        assertThat(KakaoLocalDto.Coordinate.builder().build().sigunguCode()).isNull();
        assertThat(KakaoLocalDto.Coordinate.builder().bCode("111").build().sigunguCode()).isNull();
    }
}
