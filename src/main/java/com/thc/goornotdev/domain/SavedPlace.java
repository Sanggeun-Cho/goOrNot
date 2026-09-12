package com.thc.goornotdev.domain;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.SavedPlaceDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 사용자가 하트로 저장한 장소.
 *
 * 같은 사용자가 같은 장소를 두 번 저장할 수 없도록 (user_id, content_id) 에 UNIQUE 를 건다.
 * 서비스에서 먼저 검사하지만, 동시 요청은 애플리케이션 검사만으로 막히지 않으므로 DB 제약을 최종 방어선으로 둔다.
 */
@Entity @Getter
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saved_place_user_content",
                columnNames = {"user_id", "content_id"}
        )
)
public class SavedPlace extends AuditingFields {
    /**
     * 저장한 사용자 (FK 는 Long 필드로 직접 보유).
     * 장소 저장은 로그인 필수라 null 이 될 수 없다.
     * UNIQUE 키의 일부라 생성 이후 바뀌면 안 된다 (Setter 없음)
     */
    @Column(nullable = false)
    Long userId;

    /**
     * TourAPI 콘텐츠 ID. UNIQUE 키의 일부라 변경 불가 (Setter 없음)
     */
    @Column(nullable = false)
    String contentId;

    /**
     * 어느 세션에서 저장했는지.
     * 하트를 해제했다가 다른 세션에서 다시 저장할 수 있어 갱신 가능하다
     */
    @Setter
    @Column(nullable = false)
    Long throwSessionId;

    /** TourAPI 응답을 그대로 저장한 장소 정보 (실시간 재조회 없이 마이페이지에서 바로 보여주기 위함) */
    @Setter
    String category;
    @Setter
    String placeName;
    @Setter
    String address;
    @Setter
    Double lat;
    @Setter
    Double lng;

    protected SavedPlace() {}
    private SavedPlace(Long userId, String contentId, Long throwSessionId, String category,
                       String placeName, String address, Double lat, Double lng) {
        this.userId = userId;
        this.contentId = contentId;
        this.throwSessionId = throwSessionId;
        this.category = category;
        this.placeName = placeName;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    public static SavedPlace of(Long userId, String contentId, Long throwSessionId, String category,
                                String placeName, String address, Double lat, Double lng) {
        return new SavedPlace(userId, contentId, throwSessionId, category, placeName, address, lat, lng);
    }

    /**
     * 하트를 해제했던 장소를 다시 저장할 때 기존 행을 되살린다.
     *
     * Soft Delete 라 행이 남아 있어 그대로 INSERT 하면 UNIQUE 제약에 걸린다.
     * 새 세션에서 저장한 것이므로 세션과 장소 정보도 최신 값으로 덮는다.
     */
    public void restore(SavedPlaceDto.CreateReqDto param){
        setThrowSessionId(param.getThrowSessionId());
        setCategory(param.getCategory());
        setPlaceName(param.getPlaceName());
        setAddress(param.getAddress());
        setLat(param.getLat());
        setLng(param.getLng());
        setDeleted(false);
    }

    /**
     * 하트 해제 = Soft Delete.
     * 수정 API 가 없어 update(UpdateReqDto) 를 두지 않았으므로 deleted 플래그만 직접 올린다.
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
