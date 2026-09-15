package com.thc.goornotdev.external.tourapi;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * TourAPI 응답 전용 DTO.
 *
 * DTO/ 패키지의 도메인 DTO 와 절대 혼용하지 않는다.
 * 외부 API 의 필드명/구조가 바뀌어도 컨트롤러·프론트가 영향받지 않도록,
 * 도메인 Service 에서 이 객체를 도메인 DTO 로 변환해 반환한다.
 */
public class TourApiDto {

    /**
     * response.body.items.item[] 한 건.
     *
     * TourAPI 는 필드를 전부 문자열로 내려주므로(좌표 포함) 타입 변환은 도메인 변환 시점으로 미룬다.
     */
    @Getter
    @Setter
    @Builder
    public static class Item {
        String contentId;
        String contentTypeId;
        String title;
        String addr1;
        String addr2;
        String areaCode;
        String sigunguCode;
        String cat1;
        String cat2;
        String cat3;
        String firstImage;
        String firstImage2;
        String mapX;        // 경도(lng)
        String mapY;        // 위도(lat)
        String tel;
        String dist;        // 위치기반 조회에서만 내려온다 (m 단위)
        String overview;    // detailCommon2 에서만 내려온다
        String homepage;    // detailCommon2 에서만 내려온다

        /**
         * 대표이미지 저작권 구분 코드 (Type1 = 제1유형, Type3 = 제3유형 등).
         * 이미지를 화면에 쓸 수 있는지 판단하는 근거라 반드시 함께 들고 다닌다.
         */
        String cpyrhtDivCd;

        /** 법정동 기준 시도 / 시군구 코드. regions.json 의 시군구 코드와 맞춰보는 용도 */
        String lDongRegnCd;
        String lDongSignguCd;
    }

    /**
     * 코드 조회(ldongCode2, areaCode2 등) 결과 한 건.
     */
    @Getter
    @Builder
    public static class Code {
        String code;
        String name;
    }

    /**
     * 목록 조회 결과. 페이징 정보를 함께 들고 있어야 다음 페이지 호출 여부를 판단할 수 있다.
     */
    @Getter
    @Builder
    public static class ListResult {
        List<Item> items;
        Integer numOfRows;
        Integer pageNo;
        Integer totalCount;
    }
}
