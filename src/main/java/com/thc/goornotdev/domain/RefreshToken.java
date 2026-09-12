package com.thc.goornotdev.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter
public class RefreshToken extends AuditingFields {
    /**
     * 토큰 소유자 (FK 는 Long 필드로 직접 보유)
     */
    @Setter
    @Column(nullable = false)
    Long userId;

    /**
     * 발급된 Refresh Token 원문
     */
    @Setter
    @Column(nullable = false, unique = true, length = 512)
    String content;

    protected RefreshToken() {}
    private RefreshToken(Long userId, String content) {
        this.userId = userId;
        this.content = content;
    }

    public static RefreshToken of (Long userId, String content) {
        return new RefreshToken(userId, content);
    }
}
