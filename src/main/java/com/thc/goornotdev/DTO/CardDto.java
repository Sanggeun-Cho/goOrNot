package com.thc.goornotdev.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 카테고리 안에서 뽑아 보여주는 추천 카드.
 *
 * PlaceDto 와 마찬가지로 DB 테이블이 없다.
 * 장소 자체는 PlaceDto.DetailResDto 를 그대로 재사용하고, 여기서는 그 위에
 * "몇 번 슬롯인가 / 아직 리롤할 수 있는가" 같은 카드 게임 규칙만 얹는다.
 * 장소 표현을 복제하지 않아야 TourAPI 필드가 늘어도 한 곳만 고친다.
 */
public class CardDto {

    /**
     * REQUEST
     * 카드 세트 요청.
     *
     * 지역 코드를 받지 않는다. 세션에 확정된 지역을 서버가 꺼내 쓴다 —
     * 클라이언트가 지역을 보내면 던지기로 확정한 지역을 무시하고 원하는 동네의
     * 카드를 받아볼 수 있어, 추첨을 우회하는 또 하나의 구멍이 된다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DealReqDto {
        @NotNull(message = "세션 ID 가 필요합니다.")
        Long throwSessionId;

        /** ThrowCategory 이름 (ATTRACTION / EVENT / LEISURE / STAY / FOOD) */
        @NotBlank(message = "카테고리가 필요합니다.")
        String category;
    }

    /**
     * REQUEST
     * 카드 한 장 다시 뽑기.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class RerollReqDto {
        @NotNull(message = "세션 ID 가 필요합니다.")
        Long throwSessionId;

        @NotBlank(message = "카테고리가 필요합니다.")
        String category;

        /** 바꿀 카드의 슬롯 번호. 0부터 시작한다 */
        @NotNull(message = "바꿀 카드 슬롯이 필요합니다.")
        Integer slot;
    }

    /**
     * RESPONSE
     * 카드 한 장.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto {
        /** 슬롯 번호(0부터). 리롤 요청이 이 값을 그대로 돌려보낸다 */
        Integer slot;

        /**
         * 이 카드를 아직 바꿀 수 있는지.
         *
         * 슬롯당 리롤은 1회라, 한 번 쓰면 false 로 내려간다.
         * 화면이 버튼을 직접 비활성화할 수 있도록 서버가 판단해서 내려준다 —
         * 프론트가 자체 카운트를 세면 새로고침 한 번에 초기화된다.
         */
        boolean rerollable;

        PlaceDto.DetailResDto place;
    }

    /**
     * RESPONSE
     * 카드 세트 + 어떤 지역·카테고리에서 나왔는지.
     *
     * 리롤 응답도 한 장이 아니라 세트 전체를 돌려준다.
     * 화면이 부분 갱신을 조립하지 않아도 되고, 프론트가 들고 있는 상태와
     * 서버 상태가 어긋날 여지를 없애기 위해서다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class SetResDto {
        Long throwSessionId;

        String regionCode;
        String regionName;

        /** ThrowCategory 이름 */
        String category;

        /** 화면에 그대로 쓸 한글 라벨 */
        String categoryLabel;

        /** 후보 풀 크기. 리롤로 새 장소가 나올 여지가 있는지 판단하는 값이다 */
        Integer poolSize;

        /** TourAPI 가 알려준 반경 안 전체 건수. 풀이 작을 때 원인을 구분하는 용도 */
        Integer totalCount;

        /**
         * 이 세트를 언제까지 이어서 쓸 수 있는지(초).
         * 만료되면 카드도 리롤 횟수도 새로 시작한다
         */
        Long expiresInSeconds;

        /** 최대 3장. 반경 안 장소가 적으면 그보다 적게 내려간다 */
        List<DetailResDto> cards;
    }
}
