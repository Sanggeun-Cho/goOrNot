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
     * 장소 저장 데이터
     *
     * 장소 상세 값은 프론트가 TourAPI 응답을 그대로 넘겨준다고 가정한다 (서버는 실시간 연동하지 않음).
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class CreateReqDto {
        @NotNull(message = "세션 ID 는 필수입니다.")
        Long throwSessionId;

        @NotBlank(message = "콘텐츠 ID 는 필수입니다.")
        String contentId;

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
                    getLng()
            );
        }
    }

    /**
     * RESPONSE
     * 저장 장소 상세 데이터
     *
     * UpdateReqDto 는 없다. 저장 장소는 저장 / 해제만 있고 수정 API 를 두지 않는다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto extends DefaultDto.DetailResDto {
        Long userId;
        Long throwSessionId;
        String contentId;
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
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto extends DefaultDto.ListReqDto {
        Long userId;
        Long throwSessionId;
        String category;
    }
}
