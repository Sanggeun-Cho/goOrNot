package com.thc.goornotdev.repository;

import com.thc.goornotdev.domain.ThrowRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThrowRoundRepository extends JpaRepository<ThrowRound, Long> {
    /** 세션 삭제 시 하위 회차를 순회하며 Soft Delete 하기 위한 조회 */
    List<ThrowRound> findByThrowSessionIdAndDeletedFalse(Long throwSessionId);

    /**
     * 다음 roundNo 계산용. 삭제된 회차도 포함해 가장 큰 번호를 본다.
     * count 로 세면 Soft Delete 된 회차 때문에 번호가 겹칠 수 있다.
     */
    Optional<ThrowRound> findTopByThrowSessionIdOrderByRoundNoDesc(Long throwSessionId);
}
