package com.thc.goornotdev.security;

import com.thc.goornotdev.DTO.UserDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
        } catch (IOException | RuntimeException e) {
            // 본문이 깨진 것은 클라이언트 잘못이므로 400 이 맞다.
            // 여기서 예외를 던지면 필터 단계라 GlobalExceptionHandler 가 잡지 못해
            // 500 + 스택트레이스(내부 클래스 경로 노출)가 그대로 나간다.
            // null 을 반환하면 Spring Security 는 "인증이 아직 안 끝났다" 로 보고 응답을 건드리지 않는다.
            // Jackson 3 의 파싱 예외는 unchecked 라 IOException 만 잡으면 빠져나간다
            badRequest(response);

            return null;
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

    // 필터 단계 오류는 GlobalExceptionHandler 를 타지 않으므로 같은 모양의 JSON 을 직접 내려준다
    private void badRequest(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        try {
            response.getWriter().write("{\"error\":\"로그인 요청 형식이 올바르지 않습니다.\"}");
        } catch (IOException e) {
            // 응답조차 쓸 수 없는 상황(연결 끊김 등)에서는 상태 코드만 남기고 더 할 수 있는 처리가 없다
        }
    }
}
