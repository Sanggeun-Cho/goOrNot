package com.thc.goornotdev.DTO;

import com.thc.goornotdev.domain.ThrowChoice;
import com.thc.goornotdev.domain.ThrowRound;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

public class ThrowRoundDto {
    /**
     * REQUEST
     * 회차 기록 생성 데이터
     *
     * roundNo 는 받지 않는다. 클라이언트가 보내면 회차를 건너뛰거나 덮어쓸 수 있어
     * 서버가 직전 회차 + 1 로 채운다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class CreateReqDto {
        @NotNull(message = "세션 ID 는 필수입니다.")
        Long throwSessionId;

        String regionCode;
        Double lat;
        Double lng;

        @NotNull(message = "선택(choice)은 필수입니다.")
        ThrowChoice choice;

        public ThrowRound toEntity(Integer roundNo){
            return ThrowRound.of(
                    getThrowSessionId(),
                    roundNo,
                    getRegionCode(),
                    getLat(),
                    getLng(),
                    getChoice()
            );
        }
    }

    /**
     * RESPONSE
     * 회차 상세 데이터
     *
     * UpdateReqDto 는 없다. 회차 기록은 append-only 라 수정 API 를 두지 않는다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto extends DefaultDto.DetailResDto {
        Long throwSessionId;
        Integer roundNo;
        String regionCode;
        Double lat;
        Double lng;
        ThrowChoice choice;
    }

    /**
     * REQUEST
     * 회차 목록 조회 조건
     *
     * throwSessionId 는 필수다. 세션을 지정하지 않으면 소유권 검증 기준이 사라진다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto extends DefaultDto.ListReqDto {
        @NotNull(message = "세션 ID 는 필수입니다.")
        Long throwSessionId;

        ThrowChoice choice;
    }

    /**
     * RESPONSE
     * 다음 랜덤 후보에서 제외할 좌표
     *
     * 이미 뽑힌 지역을 또 뽑지 않도록 프론트가 블랙리스트로 쓴다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class CoordinateResDto {
        String regionCode;
        Double lat;
        Double lng;
    }
}
