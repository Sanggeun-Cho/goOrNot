package com.thc.goornotdev.security;

import com.thc.goornotdev.domain.RefreshToken;
import com.thc.goornotdev.exception.InvalidTokenException;
import com.thc.goornotdev.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AuthServiceImpl implements AuthService {
    private static final String SUBJECT_ACCESS = "accessToken";
    private static final String SUBJECT_REFRESH = "refreshToken";

    private final ExternalProperties externalProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    private SecretKey tokenKey;

    @PostConstruct
    public void init() {
        this.tokenKey = Keys.hmacShaKeyFor(externalProperties.getTokenSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public SecretKey getTokenKey() {
        return tokenKey;
    }

    /**
     * Access Token 생성을 위한 함수
     * Payload 에 userId 를 담는다
     */
    @Override
    public String createAccessToken(Long userId) {
        return createToken(SUBJECT_ACCESS, userId, externalProperties.getAccessTokenExpirationTime());
    }

    /**
     * Access Token 검증을 위한 함수
     */
    @Override
    public Long verifyAccessToken(String accessToken) {
        return parse(accessToken, SUBJECT_ACCESS);
    }

    /**
     * Refresh Token 생성을 위한 함수
     * 로그인 시점에 이전 토큰을 모두 폐기하고 새로 발급해 DB 에 저장한다
     */
    @Override
    @Transactional
    public String createRefreshToken(Long userId) {
        revokeRefreshToken(userId);

        String refreshToken = createToken(SUBJECT_REFRESH, userId, externalProperties.getRefreshTokenExpirationTime());

        refreshTokenRepository.save(RefreshToken.of(userId, refreshToken));

        return refreshToken;
    }

    @Override
    @Transactional
    public void revokeRefreshToken(Long userId) {
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserId(userId));
    }

    /**
     * Refresh Token 검증 함수
     * 서명·만료 검증에 더해 DB 에 살아있는 토큰인지 확인한다
     */
    @Override
    public Long verifyRefreshToken(String refreshToken) {
        refreshTokenRepository.findByContent(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("폐기되었거나 존재하지 않는 Refresh Token 입니다."));

        return parse(refreshToken, SUBJECT_REFRESH);
    }

    /**
     * Access Token 발급을 위한 함수
     * Refresh Token 이 유효하다면 Access Token 발급
     */
    @Override
    public String issueAccessToken(String refreshToken) {
        return createAccessToken(verifyRefreshToken(refreshToken));
    }

    private String createToken(String subject, Long userId, Long expirationTime) {
        Date now = new Date();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claim("id", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationTime))
                .signWith(getTokenKey())
                .compact();
    }

    // subject 까지 확인해 Refresh Token 을 Access Token 자리에 쓰는 교차 사용을 차단한다
    private Long parse(String token, String expectedSubject) {
        Claims claims = Jwts.parser()
                .verifyWith(getTokenKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!expectedSubject.equals(claims.getSubject())) {
            throw new InvalidTokenException("토큰 종류가 올바르지 않습니다.");
        }

        Long userId = claims.get("id", Long.class);
        if (userId == null) {
            throw new InvalidTokenException("토큰에 사용자 정보가 없습니다.");
        }

        return userId;
    }
}
