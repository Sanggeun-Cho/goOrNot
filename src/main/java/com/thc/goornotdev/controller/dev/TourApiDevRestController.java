package com.thc.goornotdev.controller.dev;

import com.thc.goornotdev.DTO.PlaceDto;
import com.thc.goornotdev.external.kakao.KakaoLocalClient;
import com.thc.goornotdev.external.kakao.KakaoLocalDto;
import com.thc.goornotdev.external.tourapi.TourApiClient;
import com.thc.goornotdev.external.tourapi.TourApiDto;
import com.thc.goornotdev.service.PlaceService;
import com.thc.goornotdev.util.RegionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 외부 연동 점검 전용 임시 컨트롤러. 제출 전에 controller/dev 폴더째 삭제한다.
 *
 * 이름은 TourApi 로 시작하지만 지금은 TourAPI / 카카오 로컬 / 지역 추첨 / 장소 조회를 모두 다룬다.
 * 점검 도구를 한 파일에 모아둬야 제출 전에 "이 폴더만 지우면 끝" 이 되기 때문이다.
 *
 * CLAUDE.md 규칙을 의도적으로 어긴 부분:
 * "외부 응답 DTO 는 컨트롤러에 그대로 노출하지 않는다" 가 원칙이지만,
 * 이 화면의 목적 자체가 "실제 응답 필드가 우리가 가정한 이름으로 내려오는지"를 눈으로 확인하는 것이라
 * 도메인 DTO 로 변환하면 확인이 안 된다. 그래서 TourApiDto / KakaoLocalDto 를 그대로 내보낸다.
 * 실제 기능(던지기 결과 장소 조회 등)은 반드시 도메인 Service 에서 변환해 내보낸다.
 * — 단 /place/list 는 예외다. 그 엔드포인트의 목적이 "PlaceService 의 변환 결과" 확인이라
 *   일부러 도메인 DTO(PlaceDto)를 그대로 내려준다.
 *
 * 안전장치 두 겹:
 *  1) external.tourapi.dev-tools=true 일 때만 빈으로 등록된다
 *  2) SecurityConfig 의 anyRequest().authenticated() 대상이라 로그인해야 호출된다
 *     — 열어두면 개발계정 일일 한도(1,000건)를 누구나 소모시킬 수 있다
 */
@ConditionalOnProperty(name = "external.tourapi.dev-tools", havingValue = "true")
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@RestController
public class TourApiDevRestController {
    /** 추첨 시뮬레이터가 한 번에 돌릴 수 있는 최대 횟수. 인메모리라 비용은 없지만 응답이 커진다 */
    private static final int MAX_DRAW_COUNT = 5000;

    /** 응답에 담아 보여줄 추첨 결과 최대 건수 */
    private static final int MAX_DRAW_SAMPLES = 50;

    private final TourApiClient tourApiClient;
    private final KakaoLocalClient kakaoLocalClient;
    private final RegionCatalog regionCatalog;
    private final PlaceService placeService;

    /* ── TourAPI ─────────────────────────────────────────── */

    /** 시나리오 1 - 지역기반 목록 조회 */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tourapi/area-based-list")
    public ResponseEntity<TourApiDto.ListResult> areaBasedList(
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(required = false) String arrange,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String areaCode) {

        return ResponseEntity.ok(
                tourApiClient.areaBasedList(numOfRows, pageNo, arrange, contentTypeId, areaCode));
    }

    /**
     * 시나리오 2 - 공통정보 상세 조회.
     * 결과가 1건이라 item 이 배열이 아니라 객체로 내려오는 경로를 타는지 확인하는 용도.
     * 없는 contentId 를 넣으면 ExternalApiException → 502 가 되어 시나리오 3 확인에도 쓴다
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tourapi/detail-common")
    public ResponseEntity<TourApiDto.Item> detailCommon(@RequestParam String contentId) {
        return ResponseEntity.ok(tourApiClient.detailCommon(contentId));
    }

    /**
     * 시나리오 5 - 법정동 코드 조회 (ldongCode2).
     *
     * 시딩이 시군구 목록을 어디서 가져왔는지 눈으로 확인하는 용도다.
     * lDongRegnCd 를 비우면 시도 목록, 시도 코드를 넣으면 그 아래 시군구 목록이 온다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tourapi/ldong-code")
    public ResponseEntity<List<TourApiDto.Code>> ldongCode(
            @RequestParam(required = false) String lDongRegnCd) {

        return ResponseEntity.ok(tourApiClient.ldongCode(lDongRegnCd));
    }

    /**
     * 시나리오 7 - 서비스 분류코드 조회 (categoryCode2).
     *
     * ThrowCategory 에 쓸 cat1/cat2/cat3 코드를 눈으로 확정하기 위한 도구다.
     * 특히 "공원" 이 어느 분류 아래에 있는지는 매뉴얼만 봐서는 알 수 없어
     * 실제 응답을 받아 확인해야 한다.
     *
     * 비우고 부르면 대분류, cat1 을 넣으면 중분류, cat1+cat2 를 넣으면 소분류가 온다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tourapi/category-code")
    public ResponseEntity<List<TourApiDto.Code>> categoryCode(
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String cat1,
            @RequestParam(required = false) String cat2) {

        return ResponseEntity.ok(tourApiClient.categoryCode(contentTypeId, cat1, cat2));
    }

    /* ── 카카오 로컬 ─────────────────────────────────────── */

    /**
     * 시나리오 6 - 주소 → 좌표 + 법정동 코드.
     *
     * 매칭이 0건이면 예외가 아니라 204 로 내린다. 시딩에서도 null 을 "건너뛸 대상" 으로 다루기 때문에
     * 화면에서도 실패(5xx)와 구분되어 보여야 한다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/kakao/geocode")
    public ResponseEntity<KakaoLocalDto.Coordinate> geocode(@RequestParam String address) {
        KakaoLocalDto.Coordinate coordinate = kakaoLocalClient.geocode(address);

        return (coordinate == null) ? ResponseEntity.noContent().build() : ResponseEntity.ok(coordinate);
    }

    /* ── 지역 추첨 ───────────────────────────────────────── */

    /**
     * 시나리오 7 - 가중치 추첨 시뮬레이터.
     *
     * ThrowRoundService.draw() 는 세션 소유권 검증이 걸려 있어 분포를 보기 어렵다.
     * 여기서는 카탈로그를 직접 여러 번 돌려 "인기 지역이 가중치만큼만 나오는지" 를 눈으로 본다.
     * 외부 API 를 호출하지 않는 인메모리 연산이라 일일 한도와 무관하다.
     *
     * @param count    추첨 횟수
     * @param excluded 제외할 시군구 코드 (쉼표 구분). "말래(재던지기)" 상황 재현용
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/region/draw")
    public ResponseEntity<Map<String, Object>> draw(
            @RequestParam(defaultValue = "1") int count,
            @RequestParam(required = false) String excluded) {

        List<String> excludedCodes = splitCodes(excluded);
        int times = Math.max(1, Math.min(count, MAX_DRAW_COUNT));

        List<Map<String, Object>> samples = new ArrayList<>();
        Map<String, Integer> frequency = new LinkedHashMap<>();
        int popularHits = 0;

        for (int i = 0; i < times; i++) {
            RegionCatalog.Region region = regionCatalog.draw(excludedCodes);

            frequency.merge(region.code(), 1, Integer::sum);

            if (isPopular(region)) {
                popularHits++;
            }

            if (samples.size() < MAX_DRAW_SAMPLES) {
                samples.add(regionView(region));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("catalogSize", regionCatalog.size());
        body.put("excludedCount", excludedCodes.size());
        body.put("count", times);
        body.put("samples", samples);
        body.put("distinctCount", frequency.size());
        body.put("popularHits", popularHits);
        body.put("popularRatio", (times == 0) ? 0 : (double) popularHits / times);
        body.put("expectedPopularRatio", expectedPopularRatio(excludedCodes));

        return ResponseEntity.ok(body);
    }

    /* ── 장소 조회 (PlaceService) ────────────────────────── */

    /**
     * 시나리오 8 - 확정 지역 주변 장소 목록.
     *
     * 여기만 도메인 DTO(PlaceDto)를 그대로 내려준다. 이 엔드포인트의 목적이
     * "TourApiDto 가 PlaceDto 로 제대로 변환되는가" 확인이기 때문이다.
     * 좌표는 클라이언트가 못 보낸다. regionCode 로 regions.json 의 확정 좌표를 서버가 꺼내 쓴다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/place/list")
    public ResponseEntity<PlaceDto.ListResDto> placeList(
            @RequestParam String regionCode,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String arrange,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer numOfRows,
            @RequestParam(required = false) Integer pageNo) {

        return ResponseEntity.ok(placeService.list(PlaceDto.ListReqDto.builder()
                .regionCode(regionCode)
                .contentTypeId(contentTypeId)
                .arrange(arrange)
                .radius(radius)
                .numOfRows(numOfRows)
                .pageNo(pageNo)
                .build()));
    }

    /**
     * 화면의 지역 선택 상자를 채우기 위한 카탈로그 덤프.
     * 인메모리라 외부 호출이 없다.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/region/list")
    public ResponseEntity<List<Map<String, Object>>> regionList() {
        return ResponseEntity.ok(regionCatalog.regions().stream()
                .map(this::regionView)
                .toList());
    }

    /* ── 내부 ────────────────────────────────────────────── */

    private Map<String, Object> regionView(RegionCatalog.Region region) {
        Map<String, Object> view = new LinkedHashMap<>();

        view.put("code", region.code());
        view.put("name", region.name());
        view.put("lat", region.lat());
        view.put("lng", region.lng());
        view.put("weight", region.weight());
        view.put("popular", isPopular(region));

        return view;
    }

    private boolean isPopular(RegionCatalog.Region region) {
        return region.weight() != null && region.weight() < RegionCatalog.WEIGHT_DEFAULT;
    }

    /** 기대 확률. 관측 비율과 나란히 보여줘야 "덜 나온다" 를 판정할 수 있다 */
    private double expectedPopularRatio(List<String> excludedCodes) {
        double total = 0;
        double popular = 0;

        for (RegionCatalog.Region region : regionCatalog.regions()) {
            if (excludedCodes.contains(region.code())) {
                continue;
            }

            double weight = (region.weight() == null || region.weight() <= 0)
                    ? RegionCatalog.WEIGHT_DEFAULT : region.weight();

            total += weight;

            if (isPopular(region)) {
                popular += weight;
            }
        }

        return (total <= 0) ? 0 : popular / total;
    }

    private List<String> splitCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
