package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.CardDto;
import com.thc.goornotdev.security.PrincipalDetails;
import com.thc.goornotdev.service.CardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카테고리별 추천 카드.
 *
 * 세션·회차와 달리 로그인을 요구한다.
 * 기획상 지역 확정까지가 무료 후크고 카테고리·카드부터가 회원 전용 가치이며,
 * 실제로 TourAPI 일일 한도를 쓰는 구간도 여기다.
 *
 * deviceId 헤더를 함께 받는 이유:
 * 비로그인으로 시작한 세션은 로그인 시점에 userId 로 연결되는데, 그 연결이
 * 아직 안 된 상태로 카드 요청이 먼저 올 수 있다. 그때 deviceId 로도 소유권을
 * 확인할 수 있어야 "내 세션인데 남의 것" 이라는 응답을 피한다.
 */
@RequiredArgsConstructor
@RequestMapping("/api/card")
@RestController
public class CardRestController {
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final CardService cardService;

    // 요청한 사용자의 ID 반환
    public Long getUserId(PrincipalDetails principalDetails) {
        if (principalDetails != null && principalDetails.getUser() != null) {
            return principalDetails.getUser().getId();
        }

        return null;
    }

    /**
     * 카테고리의 카드 세트를 받는다.
     *
     * 조회처럼 보이지만 POST 다.
     *  - 처음 호출이면 서버가 후보 풀을 만들고 카드를 깐다. 서버 상태가 바뀐다
     *  - GET 으로 두면 브라우저·프록시가 캐시할 수 있는데, 카드 세트는
     *    리롤로 계속 바뀌는 값이라 캐시된 응답이 실제 상태와 어긋난다
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/deal")
    public ResponseEntity<CardDto.SetResDto> deal(@Valid @RequestBody CardDto.DealReqDto param,
                                                  @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                  @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(cardService.deal(param, getUserId(principalDetails), deviceId));
    }

    /**
     * 카드 한 장을 다시 뽑는다. 슬롯당 1회.
     * 응답은 바뀐 한 장이 아니라 세트 전체다.
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/reroll")
    public ResponseEntity<CardDto.SetResDto> reroll(@Valid @RequestBody CardDto.RerollReqDto param,
                                                    @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                    @RequestHeader(value = DEVICE_ID_HEADER, required = false) String deviceId) {
        return ResponseEntity.ok(cardService.reroll(param, getUserId(principalDetails), deviceId));
    }
}
