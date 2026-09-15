package com.thc.goornotdev.service;

import com.thc.goornotdev.DTO.PlaceDto;

/**
 * 확정된 지역 주변의 장소 추천.
 *
 * TourAPI 연동은 이 계층에서 끝난다. 컨트롤러와 프론트는 PlaceDto 만 본다.
 * 반대로 external/ 패키지는 우리 도메인 DTO 를 모른다. 변환은 여기서만 일어난다.
 */
public interface PlaceService {
    /**
     * 지역 코드 주변 장소 목록.
     *
     * 좌표는 클라이언트가 보내는 값이 아니라 regions.json 의 확정 좌표를 쓴다.
     * 좌표를 받아주면 추첨 결과를 무시하고 원하는 동네만 찍어볼 수 있기 때문이다.
     *
     * @param param 지역 코드 + 선택 조건(콘텐츠 타입, 반경, 페이징)
     * @return 지역 정보와 장소 목록
     */
    PlaceDto.ListResDto list(PlaceDto.ListReqDto param);
}
