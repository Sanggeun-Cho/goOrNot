package com.thc.goornotdev.repository;

import com.thc.goornotdev.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByContent(String content);

    List<RefreshToken> findByUserId(Long userId);
}
