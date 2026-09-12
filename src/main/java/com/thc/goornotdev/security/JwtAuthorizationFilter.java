package com.thc.goornotdev.security;

import com.thc.goornotdev.domain.User;
import com.thc.goornotdev.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;

public class JwtAuthorizationFilter extends BasicAuthenticationFilter {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ExternalProperties externalProperties;

    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, UserRepository userRepository,
                                  AuthService authService, ExternalProperties externalProperties) {
        super(authenticationManager);
        this.userRepository = userRepository;
        this.authService = authService;
        this.externalProperties = externalProperties;
    }

    /**
     *  권한 인가를 위한 함수.
     *  Access Token 을 검증하고 유효하면 Authentication 을 직접 생성해 SecurityContextHolder 에 넣는다.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String accessToken = externalProperties.stripPrefix(request.getHeader(externalProperties.getAccessKey()));

        if (accessToken == null) {
            chain.doFilter(request, response);
            return;
        }

        Long userId = authService.verifyAccessToken(accessToken);

        User userEntity = userRepository.findById(userId).orElse(null);

        // 존재하지 않거나 탈퇴한 계정의 토큰은 인증 없이 통과시켜 인가 단계에서 401 로 처리한다
        if (userEntity == null || Boolean.TRUE.equals(userEntity.getDeleted())) {
            chain.doFilter(request, response);
            return;
        }

        PrincipalDetails principalDetails = new PrincipalDetails(userEntity);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principalDetails, null, principalDetails.getAuthorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
