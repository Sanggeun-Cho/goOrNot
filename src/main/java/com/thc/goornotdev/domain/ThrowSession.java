package com.thc.goornotdev.domain;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter
public class ThrowSession extends AuditingFields {
    /**
     * 세션 소유자 (FK 는 Long 필드로 직접 보유).
     * 로그인 전에 던진 세션은 null 이고, 로그인 완료 시 linkUser() 로 연결된다.
     */
    @Setter
    Long userId;

    /**
     * 비로그인 사용자를 식별하는 값. 요청 헤더 X-Device-Id 로 받는다.
     * 세션이 누구 것인지 판단하는 유일한 근거라 생성 이후 바뀌면 안 된다 (Setter 없음)
     */
    @Column(nullable = false)
    String deviceId;

    /**
     * 세션 시작 경로. 생성 시 확정되며 이후 바뀌지 않는다 (Setter 없음)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ThrowSource source;

    /**
     * 확정까지 던진 총 횟수. RANDOM 에서만 쓰고 SEARCH 는 null 이다
     */
    @Setter
    Integer totalCount;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ThrowStatus status;

    /**
     * 확정된 지역 정보. RANDOM 은 확정 시점에, SEARCH 는 생성 시점에 채워진다
     */
    @Setter
    String regionCode;
    @Setter
    String regionName;
    @Setter
    Double lat;
    @Setter
    Double lng;

    protected ThrowSession() {}
    private ThrowSession(Long userId, String deviceId, ThrowSource source, Integer totalCount, ThrowStatus status,
                         String regionCode, String regionName, Double lat, Double lng) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.source = source;
        this.totalCount = totalCount;
        this.status = status;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.lat = lat;
        this.lng = lng;
    }

    public static ThrowSession of (Long userId, String deviceId, ThrowSource source, Integer totalCount,
                                   ThrowStatus status, String regionCode, String regionName, Double lat, Double lng) {
        return new ThrowSession(userId, deviceId, source, totalCount, status, regionCode, regionName, lat, lng);
    }

    /**
     * 세션 정보 수정
     * 수정 가능 항목 : 상태, 총 던진 횟수, 확정 지역(regionCode / regionName / lat / lng)
     *
     * userId 는 여기서 다루지 않는다. 요청 본문으로 소유자를 바꿀 수 있으면
     * 남의 세션을 자기 것으로 가져올 수 있기 때문에 linkUser() 로만 연결한다.
     */
    public void update(ThrowSessionDto.UpdateReqDto param){
        if(param.getDeleted() != null){
            setDeleted(param.getDeleted());
        }
        if(param.getStatus() != null){
            setStatus(param.getStatus());
        }
        if(param.getTotalCount() != null){
            setTotalCount(param.getTotalCount());
        }
        if(param.getRegionCode() != null){
            setRegionCode(param.getRegionCode());
        }
        if(param.getRegionName() != null){
            setRegionName(param.getRegionName());
        }
        if(param.getLat() != null){
            setLat(param.getLat());
        }
        if(param.getLng() != null){
            setLng(param.getLng());
        }
    }

    /**
     * 로그인 완료 시 익명 세션을 회원 계정에 연결한다.
     * 이미 주인이 있는 세션은 건드리지 않는다.
     */
    public void linkUser(Long userId){
        if(getUserId() == null){
            setUserId(userId);
        }
    }

    public void delete(){
        update(ThrowSessionDto.UpdateReqDto.builder()
                .deleted(true)
                .build());
    }

    public DefaultDto.CreateResDto toCreateResDto(){
        return DefaultDto.CreateResDto.builder()
                .id(getId())
                .build();
    }
}
