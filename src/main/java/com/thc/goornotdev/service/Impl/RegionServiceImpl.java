package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.RegionDto;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.service.RegionService;
import com.thc.goornotdev.util.RegionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 지역 검색.
 *
 * MyBatis Mapper 를 쓰지 않는 유일한 조회다.
 * 지역 목록은 테이블이 아니라 {@link RegionCatalog} 가 들고 있는 메모리 데이터라
 * 탈 DB 가 없다. 후보가 수백 건 수준이라 단순 순회로 충분하다.
 *
 * 기획상 위치:
 * 지역 직접 검색은 "던지기" 를 대체하는 주 경로가 아니라 보조 수단이다.
 * 랜덤이 부담스러운 사용자를 위한 최소한의 출구라서 기능을 일부러 얇게 유지한다.
 * (정렬 옵션·필터·페이징 없음)
 */
@RequiredArgsConstructor
@Service
public class RegionServiceImpl implements RegionService {

    /** 한 번에 내려주는 최대 건수. 목록이 길어지면 고르기만 더 어려워진다 */
    public static final int MAX_RESULTS = 20;

    /** 너무 짧은 검색어는 사실상 전체 조회라 막는다 */
    public static final int MIN_KEYWORD_LENGTH = 2;

    private final RegionCatalog regionCatalog;

    @Override
    public List<RegionDto.DetailResDto> list(RegionDto.ListReqDto param) {
        String keyword = (param == null || param.getKeyword() == null) ? "" : param.getKeyword().trim();

        if (keyword.length() < MIN_KEYWORD_LENGTH) {
            throw new InvalidRequestException("검색어는 " + MIN_KEYWORD_LENGTH + "글자 이상 입력해 주세요.");
        }

        // "강원 속초" 처럼 띄어 쓴 검색어도 잡히도록 토큰을 전부 포함하는지로 판단한다.
        // name 이 "강원특별자치도 속초시" 라 통째로 비교하면 공백 위치가 달라 놓친다
        String[] tokens = keyword.split("\\s+");

        return regionCatalog.regions().stream()
                .filter(region -> matches(region, tokens))
                .limit(MAX_RESULTS)
                .map(this::toDetailResDto)
                .toList();
    }

    /* ── 내부 공통 ───────────────────────────────────────── */

    private boolean matches(RegionCatalog.Region region, String[] tokens) {
        String name = region.name();

        if (name == null) {
            return false;
        }

        return Arrays.stream(tokens).allMatch(name::contains);
    }

    private RegionDto.DetailResDto toDetailResDto(RegionCatalog.Region region) {
        return RegionDto.DetailResDto.builder()
                .code(region.code())
                .name(region.name())
                .sidoName(region.sidoName())
                .sigunguName(region.sigunguName())
                .lat(region.lat())
                .lng(region.lng())
                .build();
    }
}
