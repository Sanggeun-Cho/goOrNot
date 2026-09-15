package com.thc.goornotdev.config;

import com.thc.goornotdev.repository.UserRepository;
import com.thc.goornotdev.security.AuthService;
import com.thc.goornotdev.security.ExternalProperties;
import com.thc.goornotdev.security.FilterExceptionHandlerFilter;
import com.thc.goornotdev.security.JwtAuthenticationFilter;
import com.thc.goornotdev.security.JwtAuthorizationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ExternalProperties externalProperties;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(authenticationManager, objectMapper, authService, externalProperties);
        jwtAuthenticationFilter.setFilterProcessesUrl("/api/login");

        JwtAuthorizationFilter jwtAuthorizationFilter =
                new JwtAuthorizationFilter(authenticationManager, userRepository, authService, externalProperties);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login", "/api/auth").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/user").permitAll()
                        // 로그인 전에도 던지기가 가능해야 하므로 익명 요청을 허용한다.
                        // 실제 접근 제어는 서비스 계층의 소유권 검증(userId 또는 deviceId 일치)이 담당한다.
                        // 장소 저장(SavedPlace)은 로그인 필수라 여기에 넣지 않는다
                        .requestMatchers("/api/throw-session/**", "/api/throw-round/**").permitAll()
                        // 정적 리소스는 인증 대상이 아니다
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // 화면은 모두 열어두고, 로그인 필요 여부는 프론트 가드와 API 권한으로 판단한다
                        .requestMatchers("/", "/index", "/user/**").permitAll()
                        // TourAPI 연동 점검용 임시 화면. HTML 만 열어주고 실제 호출(/api/dev/**)은
                        // 아래 anyRequest().authenticated() 에 걸려 로그인해야 쓸 수 있다.
                        // 화면 자체도 external.tourapi.dev-tools=true 일 때만 매핑된다 (제출 전 삭제 대상)
                        .requestMatchers("/dev/**").permitAll()
                        // sendError 로 내려가는 응답은 컨테이너가 /error 로 포워딩한다.
                        // 여기를 막아두면 로그인 실패(401)나 잘못된 요청(400)이 전부 403 으로 덮인다
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                // 등록된 EntryPoint 가 없으면 Spring Security 가 403 으로 폴백한다.
                // 프론트가 401 을 보고 Access Token 을 재발급하므로 비인증 요청은 401 로 내려야 한다
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(this::unauthorized)
                )
                // 토큰 예외를 401 로 변환하려면 인가 필터보다 앞에 있어야 한다
                .addFilterBefore(new FilterExceptionHandlerFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilter(jwtAuthenticationFilter);

        return http.build();
    }

    // 인증이 없거나 실패한 요청에 대한 응답. GlobalExceptionHandler 와 같은 모양으로 내려준다
    private void unauthorized(HttpServletRequest request, HttpServletResponse response,
                              AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"로그인이 필요합니다.\"}");
    }
}
