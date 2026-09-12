package com.thc.goornotdev.repository;

import com.thc.goornotdev.domain.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {
    /** 세션 삭제 시 하위 저장 장소를 순회하며 Soft Delete 하기 위한 조회 */
    List<SavedPlace> findByThrowSessionIdAndDeletedFalse(Long throwSessionId);

    /**
     * 중복 저장 검사용. deleted 조건을 일부러 걸지 않는다.
     * UNIQUE(user_id, content_id) 는 Soft Delete 된 행도 차지하고 있어서
     * 삭제 여부와 무관하게 행의 존재 자체를 봐야 한다.
     */
    Optional<SavedPlace> findByUserIdAndContentId(Long userId, String contentId);
}
