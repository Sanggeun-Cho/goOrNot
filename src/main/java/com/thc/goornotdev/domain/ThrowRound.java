package com.thc.goornotdev.domain;

import com.thc.goornotdev.DTO.DefaultDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

/**
 * 던지기 한 회차 기록.
 *
 * 한 번 남기면 바뀌지 않는 append-only 기록이라 update() 와 개별 @Setter 가 없다.
 * 기록을 고칠 수 있으면 "몇 번 만에 확정했는지"가 신뢰할 수 없는 값이 된다.
 */
@Entity @Getter
public class ThrowRound extends AuditingFields {
    /** 소속 세션 (FK 는 Long 필드로 직접 보유) */
    @Column(nullable = false)
    Long throwSessionId;

    /** 세션 내 회차 번호. 클라이언트 값을 믿지 않고 서버가 채운다 */
    @Column(nullable = false)
    Integer roundNo;

    /** 이 회차에서 뽑힌 지역 */
    String regionCode;
    Double lat;
    Double lng;

    /** 사용자의 선택 (GO = 확정, AGAIN = 다시 던지기) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ThrowChoice choice;

    protected ThrowRound() {}
    private ThrowRound(Long throwSessionId, Integer roundNo, String regionCode,
                       Double lat, Double lng, ThrowChoice choice) {
        this.throwSessionId = throwSessionId;
        this.roundNo = roundNo;
        this.regionCode = regionCode;
        this.lat = lat;
        this.lng = lng;
        this.choice = choice;
    }

    public static ThrowRound of(Long throwSessionId, Integer roundNo, String regionCode,
                                Double lat, Double lng, ThrowChoice choice) {
        return new ThrowRound(throwSessionId, roundNo, regionCode, lat, lng, choice);
    }

    /**
     * Soft Delete.
     * 수정 가능한 필드가 없어 update(UpdateReqDto) 를 두지 않았으므로
     * 다른 도메인과 달리 deleted 플래그만 직접 내린다.
     */
    public void delete(){
        setDeleted(true);
    }

    public DefaultDto.CreateResDto toCreateResDto(){
        return DefaultDto.CreateResDto.builder()
                .id(getId())
                .build();
    }
}
