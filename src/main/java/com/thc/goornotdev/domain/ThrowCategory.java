package com.thc.goornotdev.domain;

import com.thc.goornotdev.exception.InvalidRequestException;

/**
 * 지역이 확정된 뒤 고르는 카드 카테고리.
 *
 * 각 값은 TourAPI 의 contentTypeId 하나에 그대로 대응한다.
 * 프론트는 이 enum 이름만 쓰고 숫자 코드는 모른다. TourAPI 가 코드 체계를 바꾸더라도
 * 화면은 건드리지 않고 이 파일만 고치면 되도록 경계를 여기에 둔다.
 *
 * [2026-09-17 확정] "공원" 은 카테고리에서 제외했다.
 * categoryCode2 로 실제 코드를 확인한 결과 공원은 contentTypeId 가 따로 없고
 * cat3 다섯 개(A01010100 국립 / A01010200 도립 / A01010300 군립 / A02020600 테마 / A02020700 공원)로
 * 흩어져 있어, 한 번의 호출로 모으지 못하고 cat1 이 A01 과 A02 로 갈린다.
 * 산책할 만한 곳을 보여주자는 원래 의도에 비해 호출 비용과 구현 복잡도가 과해서 접었다.
 * 되살린다면 cat3 화이트리스트 + 후처리 필터가 출발점이다.
 */
public enum ThrowCategory {
    /** 관광지. 자연·인문 관광지가 모두 들어온다 */
    ATTRACTION("12", "관광지"),

    /** 축제/공연/행사. 기간이 지난 항목이 섞일 수 있어 화면에서 날짜 확인이 필요하다 */
    EVENT("15", "행사"),

    /** 레포츠 */
    LEISURE("28", "레포츠"),

    /** 숙박 */
    STAY("32", "숙박"),

    /** 음식점 */
    FOOD("39", "음식");

    private final String contentTypeId;
    private final String label;

    ThrowCategory(String contentTypeId, String label) {
        this.contentTypeId = contentTypeId;
        this.label = label;
    }

    public String getContentTypeId() {
        return contentTypeId;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 요청 문자열 → enum. 모르는 값이면 null.
     *
     * 값이 틀린 게 오류인 자리(카드 요청)와 오류가 아닌 자리(저장된 문자열로 캐시를 비우는 등)가
     * 둘 다 있어서, 판단은 부르는 쪽에 맡기고 여기서는 찾기만 한다.
     */
    public static ThrowCategory find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (ThrowCategory category : values()) {
            if (category.name().equalsIgnoreCase(name.trim())) {
                return category;
            }
        }

        return null;
    }

    /**
     * 요청 문자열 → enum. 모르는 값이면 400.
     *
     * valueOf 를 그대로 쓰면 오타 하나에 IllegalArgumentException 이 나고 500 으로 떨어진다.
     * 잘못된 입력은 클라이언트 잘못이므로 400 이 되도록 InvalidRequestException 으로 바꾼다.
     */
    public static ThrowCategory from(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("카테고리가 필요합니다.");
        }

        ThrowCategory found = find(name);

        if (found == null) {
            throw new InvalidRequestException("알 수 없는 카테고리입니다 : " + name);
        }

        return found;
    }
}
