package com.thc.goornotdev.controller;

import com.thc.goornotdev.exception.InvalidTokenException;
import com.thc.goornotdev.security.AuthService;
import com.thc.goornotdev.security.ExternalProperties;
import com.thc.goornotdev.security.PrincipalDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class AuthRestController {
    private final AuthService authService;
    private final ExternalProperties externalProperties;

    // 요청한 사용자의 ID 반환
    public Long getUserId(PrincipalDetails principalDetails) {
        if (principalDetails != null && principalDetails.getUser() != null) {
            return principalDetails.getUser().getId();
        }

        return null;
    }

    /**
     * Refresh Token 을 검증하고 Access Token 을 Response Header 에 담아 발급한다.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/auth")
    public ResponseEntity<Void> auth(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = externalProperties.stripPrefix(request.getHeader(externalProperties.getRefreshKey()));

        if (refreshToken == null) {
            throw new InvalidTokenException("Refresh Token 이 없습니다.");
        }

        String accessToken = authService.issueAccessToken(refreshToken);

        response.addHeader(externalProperties.getAccessKey(), externalProperties.withPrefix(accessToken));

        return ResponseEntity.ok().build();
    }

    /**
     * 로그아웃. 서버에 저장된 Refresh Token 을 폐기한다.
     * Access Token 은 만료(30분)까지 유효하지만 재발급 경로가 끊기므로 세션은 그 시점에 종료된다.
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal PrincipalDetails principalDetails) {
        authService.revokeRefreshToken(getUserId(principalDetails));

        return ResponseEntity.ok().build();
    }
}
