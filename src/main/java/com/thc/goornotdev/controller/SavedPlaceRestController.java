package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.SavedPlaceDto;
import com.thc.goornotdev.security.PrincipalDetails;
import com.thc.goornotdev.service.SavedPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 여행 중에 표시해 둔 장소 (여기 간다 / 하트).
 *
 * 회원 계정에 귀속되는 데이터라 전 구간 로그인 필수다 (SecurityConfig permitAll 대상이 아님).
 *
 * 수정 엔드포인트는 없다. 표시를 켜고 끄는 일은 POST 하나로 처리한다 —
 * 클라이언트가 "이 장소의 visited 를 켠다" 만 보내면 행이 있든 없든 서버가 알아서 맞춘다.
 */
@RequiredArgsConstructor
@RequestMapping("/api/saved-place")
@RestController
public class SavedPlaceRestController {
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final SavedPlaceService savedPlaceService;

    // 요청한 사용자의 ID 반환
    public Long getUserId(PrincipalDetails principalDetails) {
        if (principalDetails != null && principalDetails.getUser() != null) {
            return principalDetails.getUser().getId();
        }

        return null;
    }

    /**
     * 저장 시점의 세션은 아직 익명(userId == null)일 수 있어
     * 세션 소유권 확인을 위해 X-Device-Id 를 함께 받는다.
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("")
    public ResponseEntity<DefaultDto.CreateResDto> create(@Valid @RequestBody SavedPlaceDto.CreateReqDto param,
                                                          @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                          @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(savedPlaceService.create(param, getUserId(principalDetails), deviceId));
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("")
    public ResponseEntity<Void> delete(@RequestBody DefaultDto.DetailReqDto param,
                                       @AuthenticationPrincipal PrincipalDetails principalDetails) {
        savedPlaceService.delete(param, getUserId(principalDetails));

        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("")
    public ResponseEntity<SavedPlaceDto.DetailResDto> detail(DefaultDto.DetailReqDto param,
                                                             @AuthenticationPrincipal PrincipalDetails principalDetails) {
        return ResponseEntity.ok(savedPlaceService.detail(param, getUserId(principalDetails)));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/list")
    public ResponseEntity<List<SavedPlaceDto.DetailResDto>> list(SavedPlaceDto.ListReqDto param,
                                                                 @AuthenticationPrincipal PrincipalDetails principalDetails) {
        return ResponseEntity.ok(savedPlaceService.list(param, getUserId(principalDetails)));
    }
}
