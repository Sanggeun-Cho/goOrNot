package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.security.PrincipalDetails;
import com.thc.goornotdev.service.ThrowSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 던지기 세션.
 *
 * 로그인 전에도 던질 수 있어야 해서 전 구간 permitAll 이다.
 * 접근 제어는 서비스 계층의 소유권 검증(userId 또는 X-Device-Id 일치)이 담당한다.
 */
@RequiredArgsConstructor
@RequestMapping("/api/throw-session")
@RestController
public class ThrowSessionRestController {
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final ThrowSessionService throwSessionService;

    // 요청한 사용자의 ID 반환
    public Long getUserId(PrincipalDetails principalDetails) {
        if (principalDetails != null && principalDetails.getUser() != null) {
            return principalDetails.getUser().getId();
        }

        return null;
    }

    @PreAuthorize("permitAll()")
    @PostMapping("")
    public ResponseEntity<DefaultDto.CreateResDto> create(@Valid @RequestBody ThrowSessionDto.CreateReqDto param,
                                                          @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                          @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwSessionService.create(param, getUserId(principalDetails), deviceId));
    }

    @PreAuthorize("permitAll()")
    @PutMapping("")
    public ResponseEntity<Void> update(@Valid @RequestBody ThrowSessionDto.UpdateReqDto param,
                                       @AuthenticationPrincipal PrincipalDetails principalDetails,
                                       @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        throwSessionService.update(param, getUserId(principalDetails), deviceId);

        return ResponseEntity.ok().build();
    }

    /**
     * 로그인 완료 시 익명 세션을 회원 계정에 연결.
     * 소유자를 바꾸는 동작이라 일반 update 와 섞지 않고 별도 엔드포인트로 분리했다.
     */
    @PreAuthorize("hasRole('USER')")
    @PutMapping("/user")
    public ResponseEntity<Void> linkUser(@RequestBody DefaultDto.DetailReqDto param,
                                         @AuthenticationPrincipal PrincipalDetails principalDetails,
                                         @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        throwSessionService.linkUser(param, getUserId(principalDetails), deviceId);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("permitAll()")
    @DeleteMapping("")
    public ResponseEntity<Void> delete(@RequestBody ThrowSessionDto.UpdateReqDto param,
                                       @AuthenticationPrincipal PrincipalDetails principalDetails,
                                       @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        throwSessionService.delete(param, getUserId(principalDetails), deviceId);

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("permitAll()")
    @GetMapping("")
    public ResponseEntity<ThrowSessionDto.DetailResDto> detail(DefaultDto.DetailReqDto param,
                                                               @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                               @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwSessionService.detail(param, getUserId(principalDetails), deviceId));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/list")
    public ResponseEntity<List<ThrowSessionDto.DetailResDto>> list(ThrowSessionDto.ListReqDto param,
                                                                   @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                                   @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwSessionService.list(param, getUserId(principalDetails), deviceId));
    }
}
