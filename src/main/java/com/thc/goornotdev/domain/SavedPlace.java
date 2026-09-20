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
 * 사용자가 여행 중에 표시해 둔 장소.
 *
 * 표시는 두 가지이고 서로 독립이다 (둘 다 켜질 수 있다).
 *   visited : "여기 간다" — 그 여행에서 실제로 간 곳. 내 여행의 본체다
 *   wished  : 하트(찜) — 지금 가진 않지만 마음에 든 곳. 선호도 자료로도 쓴다
 *
 * [2026-09-19 변경] UNIQUE 를 (user_id, content_id) 에서 세션까지 포함한
 * (user_id, throw_session_id, content_id) 로 옮겼다.
 * 계정 단위로 잠가두면 작년 속초 여행에서 간 횟집을 올해 속초 여행에 다시 기록할 수 없는데,
 * "그 여행에서 어디를 갔는가" 를 남기는 게 목적이므로 여행마다 한 번이 맞다.
 * 서비스에서 먼저 검사하지만 동시 요청은 애플리케이션 검사만으로 막히지 않아 DB 제약을 최종 방어선으로 둔다.
 */
@Entity @Getter
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_saved_place_session_content",
                columnNames = {"user_id", "throw_session_id", "content_id"}
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
     * 어느 여행(세션)에서 표시한 것인지.
     * UNIQUE 키의 일부가 되면서 불변이 됐다 — 표시를 다른 여행으로 옮기는 동작은 없고,
     * 다른 여행에서 같은 곳을 고르면 그 여행의 행이 따로 생긴다 (Setter 없음)
     */
    @Column(nullable = false)
    Long throwSessionId;

    /** "여기 간다" 로 고른 곳인지 */
    @Setter
    @Column(nullable = false)
    boolean visited;

    /** 하트를 누른 곳인지 */
    @Setter
    @Column(nullable = false)
    boolean wished;

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
                       String placeName, String address, Double lat, Double lng,
                       boolean visited, boolean wished) {
        this.userId = userId;
        this.contentId = contentId;
        this.throwSessionId = throwSessionId;
        this.category = category;
        this.placeName = placeName;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
        this.visited = visited;
        this.wished = wished;
    }

    public static SavedPlace of(Long userId, String contentId, Long throwSessionId, String category,
                                String placeName, String address, Double lat, Double lng,
                                boolean visited, boolean wished) {
        return new SavedPlace(userId, contentId, throwSessionId, category,
                placeName, address, lat, lng, visited, wished);
    }

    /**
     * 같은 여행에서 이미 만든 행의 표시를 갱신한다.
     *
     * 한 장소에 표시가 두 개(visited/wished)라 요청은 바꾸려는 쪽만 보낸다.
     * null 은 "그대로 두라" 는 뜻이므로 값이 온 플래그만 덮는다.
     * Soft Delete 된 행도 이 경로로 되살아난다 — UNIQUE 를 비켜갈 방법이 없으니
     * 새로 넣는 대신 남아 있는 행을 다시 쓰는 것이다.
     */
    public void mark(SavedPlaceDto.CreateReqDto param){
        if (param.getVisited() != null) setVisited(param.getVisited());
        if (param.getWished() != null) setWished(param.getWished());

        setCategory(param.getCategory());
        setPlaceName(param.getPlaceName());
        setAddress(param.getAddress());
        setLat(param.getLat());
        setLng(param.getLng());
        setDeleted(false);
    }

    /** 표시가 하나도 남지 않은 행인지. 이 상태가 되면 서비스가 Soft Delete 한다 */
    public boolean isBlank(){
        return !visited && !wished;
    }

    /**
     * 표시를 모두 해제 = Soft Delete.
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
