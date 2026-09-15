package com.thc.goornotdev.external.kakao;

import lombok.Builder;
import lombok.Getter;

/**
 * 카카오 로컬 API 응답 전용 DTO.
 *
 * TourApiDto 와 마찬가지로 DTO/ 패키지의 도메인 DTO 와 혼용하지 않는다.
 */
public class KakaoLocalDto {

    /**
     * 주소 → 좌표 변환 결과 한 건.
     *
     * 카카오는 x 가 경도(lng), y 가 위도(lat)다. 응답에서는 문자열로 오지만
     * 이 값은 곧바로 좌표 계산에 쓰이므로 여기서 Double 로 변환해 넘긴다.
     */
    @Getter
    @Builder
    public static class Coordinate {
        /**
         * 매칭된 주소 문자열.
         *
         * 주의 - 이 값으로 매칭 여부를 판정하면 안 된다.
         * "서울특별시 종로구" 로 질의해도 응답은 "서울 종로구" 처럼 축약형으로 온다.
         * 정확성 판정은 {@link #bCode} 로 하고, 이 필드는 사람이 눈으로 확인할 때만 쓴다.
         */
        String addressName;

        /**
         * 법정동 코드 10자리 (예: 1111000000).
         *
         * 앞 5자리가 시군구 코드다 (11110 = 종로구).
         * TourAPI ldongCode2 의 (시도코드 + 시군구코드) 와 같은 체계라,
         * "중구" 처럼 여러 시도에 같은 이름이 있어도 이 값으로 정확히 대조할 수 있다.
         */
        String bCode;

        Double lng;
        Double lat;

        /** bCode 앞 5자리(시군구 코드). 값이 없거나 짧으면 null */
        public String sigunguCode() {
            return (bCode == null || bCode.length() < 5) ? null : bCode.substring(0, 5);
        }
    }
}
