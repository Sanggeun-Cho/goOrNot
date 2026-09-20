package com.thc.goornotdev.DTO;

import com.thc.goornotdev.domain.SavedPlace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

public class SavedPlaceDto {
    /**
     * REQUEST
     * 장소 표시 데이터
     *
     * 장소 상세 값은 프론트가 TourAPI 응답을 그대로 넘겨준다고 가정한다 (서버는 실시간 연동하지 않음).
     *
     * visited / wished 를 primitive 가 아닌 Boolean 으로 둔 이유:
     * 하트만 누른 요청에서 visited 가 false 로 내려오면 이미 켜둔 "여기 간다" 가 꺼진다.
     * null = "건드리지 말 것" 으로 읽어야 두 표시를 서로 독립으로 다룰 수 있다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class CreateReqDto {
        @NotNull(message = "세션 ID 는 필수입니다.")
        Long throwSessionId;

        @NotBlank(message = "콘텐츠 ID 는 필수입니다.")
        String contentId;

        Boolean visited;
        Boolean wished;

        String category;
        String placeName;
        String address;
        Double lat;
        Double lng;

        /** userId 는 클라이언트 값이 아니라 요청자 정보에서 받아 채운다 */
        public SavedPlace toEntity(Long userId){
            return SavedPlace.of(
                    userId,
                    getContentId(),
                    getThrowSessionId(),
                    getCategory(),
                    getPlaceName(),
                    getAddress(),
                    getLat(),
                    getLng(),
                    Boolean.TRUE.equals(getVisited()),
                    Boolean.TRUE.equals(getWished())
            );
        }
    }

    /**
     * RESPONSE
     * 저장 장소 상세 데이터
     *
     * UpdateReqDto 는 없다. 표시를 켜고 끄는 일은 전부 CreateReqDto 한 경로로 처리한다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto extends DefaultDto.DetailResDto {
        Long userId;
        Long throwSessionId;
        String contentId;
        boolean visited;
        boolean wished;
        String category;
        String placeName;
        String address;
        Double lat;
        Double lng;
    }

    /**
     * REQUEST
     * 저장 장소 목록 조회 조건
     *
     * userId 는 클라이언트가 보낸 값을 쓰지 않는다. 서비스에서 요청자 ID 로 덮어쓴다.
     * throwSessionId 가 있으면 세션 상세용, 없으면 마이페이지용 전체 목록이다.
     *
     * visited / wished 는 켜진 것만 걸러내는 조건이다 (true 면 그 표시가 켜진 행만).
     * 내 여행 상세는 visited=true, 찜 목록은 wished=true 로 같은 표를 나눠 본다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto extends DefaultDto.ListReqDto {
        Long userId;
        Long throwSessionId;
        String category;
        Boolean visited;
        Boolean wished;
    }
}
