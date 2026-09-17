package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.PlaceDto;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.external.tourapi.TourApiClient;
import com.thc.goornotdev.external.tourapi.TourApiDto;
import com.thc.goornotdev.service.PlaceService;
import com.thc.goornotdev.util.RegionCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class PlaceServiceImpl implements PlaceService {

    /*
     * 튜닝 파라미터.
     *
     * 지금은 임의 고정값이다. 실제 데이터를 보면서 조정할 자리라 상수로 뽑아두고 이유를 남긴다.
     */

    /**
     * 기본 검색 반경(m).
     *
     * 시군구 중심 좌표를 기준으로 잡는데, 군 단위는 면적이 넓어 5km 로는 비는 곳이 많다.
     * TourAPI 최대값은 20,000 이라 그 안에서 넉넉하게 잡았다.
     * → 조정 후보 : 도심은 좁게(5,000), 군 지역은 넓게 바꾸는 2단계 전략
     */
    private static final int DEFAULT_RADIUS = 15000;

    /** 반경이 0건일 때 한 번 더 넓혀 보는 값. TourAPI 상한선이다 */
    private static final int FALLBACK_RADIUS = 20000;

    /** TourAPI 가 허용하는 최대 반경 */
    private static final int MAX_RADIUS = 20000;

    /** 기본 조회 건수. 화면에서 카드로 훑기 좋은 정도 */
    private static final int DEFAULT_NUM_OF_ROWS = 20;

    /**
     * 한 번에 가져올 수 있는 최대 건수.
     *
     * 이 값이 없으면 numOfRows=100000 같은 요청이 그대로 TourAPI 로 나간다.
     * 일일 한도를 쓰는 API 라 응답이 커지는 문제보다 한도가 한 번에 녹는 쪽이 더 위험하다.
     * 화면은 20건 단위로 페이징하므로 100 이면 충분하다
     */
    private static final int MAX_NUM_OF_ROWS = 100;

    /**
     * 정렬 기준.
     * O = 제목순 + 대표이미지가 있는 것만. 이미지 없는 카드가 섞이면 화면이 휑해져서 이걸 쓴다.
     * → 조정 후보 : E(거리순, 이미지 보장 없음) / S(수정일순)
     */
    private static final String DEFAULT_ARRANGE = "O";

    private final TourApiClient tourApiClient;
    private final RegionCatalog regionCatalog;

    @Override
    public PlaceDto.ListResDto list(PlaceDto.ListReqDto param) {
        RegionCatalog.Region region = resolveRegion(param);

        int radius = radiusOf(param);
        // 반경과 같은 이유로 상한을 깎아서 보낸다. 거절하지 않고 조정하는 쪽을 택한 건
        // 페이징을 크게 잡은 화면이 에러 없이 동작해야 하기 때문이다
        int numOfRows = (param.getNumOfRows() == null || param.getNumOfRows() <= 0)
                ? DEFAULT_NUM_OF_ROWS : Math.min(param.getNumOfRows(), MAX_NUM_OF_ROWS);
        int pageNo = (param.getPageNo() == null || param.getPageNo() <= 0) ? 1 : param.getPageNo();

        // TourAPI 는 X 가 경도, Y 가 위도다. 순서를 뒤집으면 엉뚱한 바다 한가운데를 찾는다
        TourApiDto.ListResult result = tourApiClient.locationBasedList(
                region.lng(), region.lat(), radius, numOfRows, pageNo,
                DEFAULT_ARRANGE, param.getContentTypeId());

        // 시골 시군구는 기본 반경 안에 등록된 관광지가 없을 수 있다. 빈 화면 대신 한 번 더 넓혀본다
        if (result.getItems().isEmpty() && radius < FALLBACK_RADIUS && pageNo == 1) {
            log.debug("반경 {}m 결과 0건 → {}m 로 재시도 ({})", radius, FALLBACK_RADIUS, region.name());

            radius = FALLBACK_RADIUS;
            result = tourApiClient.locationBasedList(
                    region.lng(), region.lat(), radius, numOfRows, pageNo,
                    DEFAULT_ARRANGE, param.getContentTypeId());
        }

        return PlaceDto.ListResDto.builder()
                .regionCode(region.code())
                .regionName(region.name())
                .lat(region.lat())
                .lng(region.lng())
                .radius(radius)
                .totalCount(result.getTotalCount())
                .places(toPlaces(result.getItems()))
                .build();
    }

    /* ── 내부 ────────────────────────────────────────────── */

    private RegionCatalog.Region resolveRegion(PlaceDto.ListReqDto param) {
        if (param.getRegionCode() == null || param.getRegionCode().isBlank()) {
            throw new InvalidRequestException("지역 코드가 필요합니다.");
        }

        RegionCatalog.Region region = regionCatalog.find(param.getRegionCode());

        if (region == null) {
            throw new NoMatchingDataException("지역 코드 : " + param.getRegionCode());
        }

        return region;
    }

    private int radiusOf(PlaceDto.ListReqDto param) {
        if (param.getRadius() == null || param.getRadius() <= 0) {
            return DEFAULT_RADIUS;
        }

        // 상한을 넘겨 보내면 TourAPI 가 파라미터 오류로 거절한다. 넘어오면 깎아서 보낸다
        return Math.min(param.getRadius(), MAX_RADIUS);
    }

    /**
     * 외부 응답 → 도메인 DTO.
     * 이 메서드가 external 패키지와 우리 도메인의 경계선이다.
     */
    private List<PlaceDto.DetailResDto> toPlaces(List<TourApiDto.Item> items) {
        List<PlaceDto.DetailResDto> places = new ArrayList<>();

        for (TourApiDto.Item item : items) {
            places.add(PlaceDto.DetailResDto.builder()
                    .contentId(item.getContentId())
                    .contentTypeId(item.getContentTypeId())
                    .placeName(item.getTitle())
                    .address(address(item))
                    .tel(item.getTel())
                    // TourAPI 의 mapX 가 경도, mapY 가 위도다
                    .lng(number(item.getMapX()))
                    .lat(number(item.getMapY()))
                    .distance(distance(item.getDist()))
                    .imageUrl(item.getFirstImage())
                    .thumbnailUrl(item.getFirstImage2())
                    .imageCopyrightCode(item.getCpyrhtDivCd())
                    .build());
        }

        return places;
    }

    /** addr1(기본 주소) + addr2(상세). addr2 는 비어 오는 경우가 많다 */
    private String address(TourApiDto.Item item) {
        String addr1 = item.getAddr1();
        String addr2 = item.getAddr2();

        if (addr1 == null || addr1.isBlank()) {
            return null;
        }

        return (addr2 == null || addr2.isBlank()) ? addr1 : addr1 + " " + addr2;
    }

    private Double number(String value) {
        try {
            return (value == null || value.isBlank()) ? null : Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 거리는 "1234.5" 같은 소수 문자열로 온다. 미터 단위 정수면 충분하다 */
    private Integer distance(String value) {
        Double parsed = number(value);

        return (parsed == null) ? null : (int) Math.round(parsed);
    }
}
