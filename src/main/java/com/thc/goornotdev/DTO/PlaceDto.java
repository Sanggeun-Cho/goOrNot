package com.thc.goornotdev.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 확정된 지역 주변의 추천 장소.
 *
 * DB 테이블이 없는 DTO 다. TourAPI 응답을 그대로 화면에 내려주기 위한 변환 결과이고,
 * 사용자가 마음에 들어 저장하면 그때 SavedPlace 로 넘어간다.
 *
 * 여기서 TourApiDto 를 쓰지 않는 이유:
 * 외부 API 필드명(contentid, firstimage, mapx ...)이 컨트롤러와 프론트까지 새어 나가면
 * TourAPI 가 스펙을 바꿀 때 화면까지 전부 고쳐야 한다. 변환은 PlaceService 한 곳에서만 한다.
 */
public class PlaceDto {

    /**
     * REQUEST
     * 지역 주변 장소 조회 조건.
     *
     * 좌표를 클라이언트가 보내지 않는다. 세션에 확정된 지역 좌표를 서버가 꺼내 쓴다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto {
        /** 지역 코드. regions.json 의 시군구 코드 5자리 */
        String regionCode;

        /**
         * TourAPI 콘텐츠 타입.
         * 12 관광지 / 14 문화시설 / 15 축제공연행사 / 25 여행코스 / 28 레포츠 / 32 숙박 / 38 쇼핑 / 39 음식점.
         * null 이면 타입을 가리지 않는다
         */
        String contentTypeId;

        /** 검색 반경(m). null 이면 서버 기본값 */
        Integer radius;

        /** 한 번에 가져올 개수. null 이면 서버 기본값 */
        Integer numOfRows;

        Integer pageNo;
    }

    /**
     * RESPONSE
     * 장소 한 건.
     *
     * DefaultDto 를 상속하지 않는다. id / deleted / createdAt 같은 우리 DB 컬럼이 없는,
     * 외부에서 받아온 읽기 전용 데이터라 공통 필드를 붙이면 의미가 없다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto {
        /** TourAPI 콘텐츠 ID. 저장 시 SavedPlace.contentId 가 된다 */
        String contentId;

        String contentTypeId;
        String placeName;
        String address;
        String tel;
        Double lat;
        Double lng;

        /** 중심 좌표로부터의 거리(m). locationBasedList2 가 계산해준다 */
        Integer distance;

        /** 대표 이미지 URL. 없을 수 있다 */
        String imageUrl;
        String thumbnailUrl;

        /**
         * 대표이미지 저작권 유형 코드.
         * Type3(제3유형)은 출처 표시 + 변경 금지 조건이라 화면 처리에 주의가 필요해 함께 내린다.
         */
        String imageCopyrightCode;
    }

    /**
     * RESPONSE
     * 장소 목록 + 추첨으로 확정된 지역 정보.
     *
     * 화면이 "속초시 주변 12곳" 처럼 보여줄 수 있도록 지역명을 함께 담는다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListResDto {
        String regionCode;
        String regionName;
        Double lat;
        Double lng;

        /** 실제 사용된 검색 반경(m) */
        Integer radius;

        /** TourAPI 가 알려준 전체 건수 (페이징 판단용) */
        Integer totalCount;

        List<DetailResDto> places;
    }
}
