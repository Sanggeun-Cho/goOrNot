package com.thc.goornotdev.security;

import javax.crypto.SecretKey;

public interface AuthService {
    SecretKey getTokenKey();

    String createAccessToken(Long userId);

    Long verifyAccessToken(String accessToken);

    String createRefreshToken(Long userId);

    void revokeRefreshToken(Long userId);

    Long verifyRefreshToken(String refreshToken);

    String issueAccessToken(String refreshToken);
}
