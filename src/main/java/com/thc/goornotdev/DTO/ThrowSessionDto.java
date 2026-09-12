package com.thc.goornotdev.DTO;

import com.thc.goornotdev.domain.ThrowSession;
import com.thc.goornotdev.domain.ThrowSource;
import com.thc.goornotdev.domain.ThrowStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

public class ThrowSessionDto {
    /**
     * REQUEST
     * 세션 생성 데이터
     *
     * source 에 따라 필수값이 갈린다 (RANDOM=totalCount, SEARCH=지역 정보).
     * 어노테이션 하나로 표현할 수 없어 교차 검증은 서비스에서 처리한다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class CreateReqDto {
        @NotNull(message = "세션 시작 경로(source)는 필수입니다.")
        ThrowSource source;

        @Min(value = 1, message = "던진 횟수는 1 이상이어야 합니다.")
        Integer totalCount;

        String regionCode;
        String regionName;
        Double lat;
        Double lng;

        /**
         * userId / deviceId 는 클라이언트 값이 아니라 요청자 정보에서 받아 채운다.
         * SEARCH 는 생성 즉시 확정, RANDOM 은 던지는 중 상태로 시작한다.
         */
        public ThrowSession toEntity(Long userId, String deviceId){
            boolean search = ThrowSource.SEARCH.equals(getSource());

            return ThrowSession.of(
                    userId,
                    deviceId,
                    getSource(),
                    search ? null : getTotalCount(),
                    search ? ThrowStatus.CONFIRMED : ThrowStatus.IN_PROGRESS,
                    getRegionCode(),
                    getRegionName(),
                    getLat(),
                    getLng()
            );
        }
    }

    /**
     * REQUEST
     * 세션 수정 데이터 (RANDOM 확정 시 사용)
     *
     * userId 는 의도적으로 빼뒀다. 소유자 변경은 linkUser 전용 API 로만 가능하다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class UpdateReqDto extends DefaultDto.UpdateReqDto {
        ThrowStatus status;

        @Min(value = 1, message = "던진 횟수는 1 이상이어야 합니다.")
        Integer totalCount;

        String regionCode;
        String regionName;
        Double lat;
        Double lng;
    }

    /**
     * RESPONSE
     * 세션 상세 데이터
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class DetailResDto extends DefaultDto.DetailResDto {
        Long userId;
        String deviceId;
        ThrowSource source;
        Integer totalCount;
        ThrowStatus status;
        String regionCode;
        String regionName;
        Double lat;
        Double lng;
    }

    /**
     * REQUEST
     * 세션 목록 조회 조건
     *
     * userId / deviceId 는 클라이언트가 보낸 값을 쓰지 않는다.
     * 서비스에서 요청자 정보로 덮어써 본인 것만 조회되도록 강제한다.
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
    public static class ListReqDto extends DefaultDto.ListReqDto {
        Long userId;
        String deviceId;
        ThrowSource source;
        ThrowStatus status;
    }
}
