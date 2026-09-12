package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowRoundDto;
import com.thc.goornotdev.security.PrincipalDetails;
import com.thc.goornotdev.service.ThrowRoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 던지기 회차 기록.
 *
 * 세션과 마찬가지로 비로그인 사용도 허용하고, 접근 제어는 서비스에서 상위 세션 소유권으로 판단한다.
 * 수정 / 단독 삭제 엔드포인트는 없다 (append-only + 세션 삭제 시 함께 정리).
 */
@RequiredArgsConstructor
@RequestMapping("/api/throw-round")
@RestController
public class ThrowRoundRestController {
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final ThrowRoundService throwRoundService;

    // 요청한 사용자의 ID 반환
    public Long getUserId(PrincipalDetails principalDetails) {
        if (principalDetails != null && principalDetails.getUser() != null) {
            return principalDetails.getUser().getId();
        }

        return null;
    }

    @PreAuthorize("permitAll()")
    @PostMapping("")
    public ResponseEntity<DefaultDto.CreateResDto> create(@Valid @RequestBody ThrowRoundDto.CreateReqDto param,
                                                          @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                          @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwRoundService.create(param, getUserId(principalDetails), deviceId));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("")
    public ResponseEntity<ThrowRoundDto.DetailResDto> detail(DefaultDto.DetailReqDto param,
                                                             @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                             @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwRoundService.detail(param, getUserId(principalDetails), deviceId));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/list")
    public ResponseEntity<List<ThrowRoundDto.DetailResDto>> list(@Valid ThrowRoundDto.ListReqDto param,
                                                                 @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                                 @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwRoundService.list(param, getUserId(principalDetails), deviceId));
    }

    /**
     * 다음 랜덤 후보에서 제외할 좌표 목록.
     * param.id 는 회차 ID 가 아니라 세션 ID 다.
     */
    @PreAuthorize("permitAll()")
    @GetMapping("/excluded")
    public ResponseEntity<List<ThrowRoundDto.CoordinateResDto>> excludedCoordinates(DefaultDto.DetailReqDto param,
                                                                                    @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                                                    @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwRoundService.excludedCoordinates(param, getUserId(principalDetails), deviceId));
    }
}
