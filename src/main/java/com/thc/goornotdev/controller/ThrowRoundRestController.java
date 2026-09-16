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
     * 이번 회차에 던질 지역을 서버가 추첨한다.
     * param.id 는 회차 ID 가 아니라 세션 ID 다.
     *
     * 조회처럼 보이지만 GET 이 아니라 POST 다.
     *  - 같은 요청이라도 부를 때마다 결과가 달라야 한다. GET 은 브라우저·프록시·CDN 이 캐시해도 되는
     *    메서드라, 캐시에 걸리면 던질 때마다 같은 지역이 나오는 치명적인 버그가 된다.
     *  - 추첨 결과를 서버가 세션별로 기록해 두고 나중에 확정 요청과 대조한다.
     *    즉 서버 상태를 바꾸므로 안전한(safe) 메서드가 아니다.
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/draw")
    public ResponseEntity<ThrowRoundDto.DrawResDto> draw(@RequestBody DefaultDto.DetailReqDto param,
                                                         @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                         @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(throwRoundService.draw(param, getUserId(principalDetails), deviceId));
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
