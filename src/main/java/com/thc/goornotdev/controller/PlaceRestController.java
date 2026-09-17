package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.PlaceDto;
import com.thc.goornotdev.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 확정된 지역 주변의 추천 장소.
 *
 * SecurityConfig 의 permitAll 목록에 없다. anyRequest().authenticated() 에 걸려
 * 로그인해야 호출할 수 있고, 엔드포인트에서 한 번 더 명시한다.
 * TourAPI 일일 한도를 쓰는 유일한 공개 API 라 익명 호출을 열어두면 한도가 그대로 소모된다.
 *
 * 다른 컨트롤러와 달리 getUserId 헬퍼가 없다.
 * 같은 지역 코드면 누구에게나 같은 결과가 나가는 조회라 요청자를 식별할 이유가 없고,
 * 쓰지도 않을 파라미터를 받으면 "유저별로 다른 결과가 나온다" 는 오해를 남긴다.
 * 로그인 여부 판단은 @PreAuthorize 가 이미 하고 있다.
 *
 * 알려진 제약 (Phase 3 리뷰 대상):
 * 세션을 거치지 않고 지역 코드만으로 조회할 수 있어, 로그인한 사용자는 던지지 않고도
 * 아무 지역의 장소를 볼 수 있다. 장소 목록 자체는 공개 관광정보라 유출 피해가 없고,
 * 지역 검색(SEARCH) 경로에서도 같은 데이터가 필요해서 지금은 이대로 둔다.
 * 한도 소모가 문제가 되면 세션 확정 지역으로 제한하는 방향을 검토한다.
 */
@RequiredArgsConstructor
@RequestMapping("/api/place")
@RestController
public class PlaceRestController {

    private final PlaceService placeService;

    /**
     * 지역 코드 주변 장소 목록.
     *
     * 좌표를 받지 않는다. regions.json 에 확정된 좌표만 쓰기 때문에
     * 클라이언트가 좌표를 보내 추첨 결과와 다른 동네를 조회할 수 없다.
     */
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/list")
    public ResponseEntity<PlaceDto.ListResDto> list(PlaceDto.ListReqDto param) {
        return ResponseEntity.ok(placeService.list(param));
    }
}
