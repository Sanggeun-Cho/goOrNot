package com.thc.goornotdev.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역(시군구) 조회 DTO.
 *
 * 다른 도메인과 달리 DefaultDto 를 상속하지 않는다.
 * 지역은 Entity 가 아니라 regions.json 에서 읽어오는 정적 참조 데이터라
 * id / deleted / createdAt 같은 공통 필드가 없다. 그래서 @SuperBuilder 도 쓰지 않는다.
 */
public class RegionDto {

    /**
     * REQUEST
     * 지역 검색 조건
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ListReqDto {
        @NotBlank(message = "검색어를 입력해 주세요.")
        String keyword;
    }

    /**
     * RESPONSE
     * 지역 하나
     *
     * 추첨 가중치(weight)는 일부러 내리지 않는다.
     * 어느 지역이 잘 뽑히는지 알려줄 이유가 없고, 화면에서 쓸 일도 없다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DetailResDto {
        /** 법정동 기준 시군구 코드 5자리 (예: 42210) */
        String code;

        /** 표시용 전체 이름 (예: 강원특별자치도 속초시) */
        String name;

        String sidoName;
        String sigunguName;
        Double lat;
        Double lng;
    }
}
