package com.thc.goornotdev.security;

import com.thc.goornotdev.DTO.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private final AuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final ExternalProperties externalProperties;

    /**
     *  로그인하려는 사용자의 자격을 확인해 토큰을 발급하는 함수.
     *  "/api/login" 으로 들어오는 요청에 실행된다.
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        UserDto.LoginReqDto loginReqDto;

        try {
            loginReqDto = objectMapper.readValue(request.getInputStream(), UserDto.LoginReqDto.class);
        } catch (IOException e) {
            throw new RuntimeException("Login Request Parsing Error", e);
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginReqDto.getUsername(), loginReqDto.getPassword());

        // 실패 시 AuthenticationException 이 던져지고 Spring Security 가 unsuccessfulAuthentication 을 호출한다
        return authenticationManager.authenticate(authenticationToken);
    }

    /**
     *  로그인 완료시 호출되는 함수.
     *  Refresh Token 을 발급해 Response Header 에 담는다.
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) {
        PrincipalDetails principalDetails = (PrincipalDetails) authResult.getPrincipal();

        String refreshToken = authService.createRefreshToken(principalDetails.getUser().getId());

        response.addHeader(externalProperties.getRefreshKey(), externalProperties.withPrefix(refreshToken));
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
