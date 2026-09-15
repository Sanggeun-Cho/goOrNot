package com.thc.goornotdev.util;

import com.thc.goornotdev.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 지역 카탈로그 + 가중치 추첨 검증.
 *
 * 실제 {@code resources/regions.json} 을 그대로 읽는다. 시딩 산출물이 깨지면
 * 여기서 먼저 걸리라고 일부러 픽스처를 따로 만들지 않았다.
 */
class RegionCatalogTest {

    private RegionCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new RegionCatalog(new ObjectMapper());
    }

    /* ── 로드 ────────────────────────────────────────────── */

    @Test
    @DisplayName("로드 - regions.json 의 시군구가 메모리에 올라온다")
    void load_readsSeedFile() {
        assertThat(catalog.size()).isGreaterThan(200);
    }

    @Test
    @DisplayName("로드 - 모든 후보는 코드 5자리와 좌표를 갖는다")
    void load_everyRegionIsUsable() {
        assertThat(catalog.regions()).allSatisfy(region -> {
            assertThat(region.code()).hasSize(5);
            assertThat(region.lat()).isNotNull();
            assertThat(region.lng()).isNotNull();
            assertThat(region.name()).isNotBlank();
        });
    }

    @Test
    @DisplayName("로드 - 시군구 코드는 중복되지 않는다")
    void load_codesAreUnique() {
        Set<String> codes = new HashSet<>();

        catalog.regions().forEach(region -> codes.add(region.code()));

        assertThat(codes).hasSize(catalog.size());
    }

    @Test
    @DisplayName("조회 - 코드로 찾는다. 없는 코드는 null")
    void find_byCode() {
        RegionCatalog.Region jongno = catalog.find("11110");

        assertThat(jongno).isNotNull();
        assertThat(jongno.sigunguName()).isEqualTo("종로구");
        assertThat(catalog.find("99999")).isNull();
        assertThat(catalog.find(null)).isNull();
    }

    /* ── 추첨 ────────────────────────────────────────────── */

    @Test
    @DisplayName("추첨 - 같은 시드면 같은 지역이 나온다")
    void draw_isReproducibleWithSeed() {
        RegionCatalog.Region first = catalog.draw(List.of(), new Random(42));
        RegionCatalog.Region second = catalog.draw(List.of(), new Random(42));

        assertThat(first.code()).isEqualTo(second.code());
    }

    @Test
    @DisplayName("추첨 - 결과는 항상 카탈로그 안의 지역이다")
    void draw_alwaysFromCatalog() {
        Random random = new Random(7);

        for (int i = 0; i < 500; i++) {
            RegionCatalog.Region drawn = catalog.draw(List.of(), random);

            assertThat(catalog.find(drawn.code())).isEqualTo(drawn);
        }
    }

    @Test
    @DisplayName("추첨 - 제외한 지역은 절대 나오지 않는다")
    void draw_neverReturnsExcluded() {
        // 앞쪽 절반을 통째로 제외한다. 누적 구간 계산이 제외분을 제대로 건너뛰는지 보는 게 목적
        List<String> excluded = catalog.regions().subList(0, catalog.size() / 2).stream()
                .map(RegionCatalog.Region::code)
                .toList();

        Random random = new Random(99);

        for (int i = 0; i < 500; i++) {
            assertThat(catalog.draw(excluded, random).code()).isNotIn(excluded);
        }
    }

    @Test
    @DisplayName("추첨 - 하나만 남기면 그 지역만 나온다")
    void draw_withSingleCandidate() {
        RegionCatalog.Region survivor = catalog.regions().get(catalog.size() / 3);

        List<String> excluded = catalog.regions().stream()
                .map(RegionCatalog.Region::code)
                .filter(code -> !code.equals(survivor.code()))
                .toList();

        assertThat(catalog.draw(excluded, new Random(1)).code()).isEqualTo(survivor.code());
    }

    @Test
    @DisplayName("추첨 - 전부 제외하면 InvalidRequestException (400)")
    void draw_allExcluded() {
        List<String> excluded = catalog.regions().stream()
                .map(RegionCatalog.Region::code)
                .toList();

        assertThatThrownBy(() -> catalog.draw(excluded, new Random(1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("추첨 - null 제외 목록은 '제외 없음' 으로 본다")
    void draw_nullExclusion() {
        assertThat(catalog.draw(null, new Random(3))).isNotNull();
    }

    /**
     * 기획안의 핵심 : 인기 지역은 낮은 가중치라 덜 나와야 한다.
     *
     * 완전 배제가 아니므로 "안 나온다" 가 아니라 "가중치 비율만큼만 나온다" 를 본다.
     * 시드를 고정해 CI 에서 흔들리지 않게 하고, 허용 오차는 넉넉히 ±40% 로 둔다.
     */
    @Test
    @DisplayName("추첨 - 인기 지역은 가중치 비율만큼만 뽑힌다")
    void draw_respectsWeight() {
        Set<String> popular = catalog.regions().stream()
                .filter(region -> region.weight() != null && region.weight() < RegionCatalog.WEIGHT_DEFAULT)
                .map(RegionCatalog.Region::code)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(popular).as("인기 지역 큐레이션이 비어 있으면 이 검증이 무의미하다").isNotEmpty();

        double totalWeight = catalog.regions().stream()
                .mapToDouble(region -> (region.weight() == null || region.weight() <= 0)
                        ? RegionCatalog.WEIGHT_DEFAULT : region.weight())
                .sum();
        double popularWeight = catalog.regions().stream()
                .filter(region -> popular.contains(region.code()))
                .mapToDouble(RegionCatalog.Region::weight)
                .sum();

        int trials = 20000;
        Random random = new Random(2024);
        int hits = 0;

        for (int i = 0; i < trials; i++) {
            if (popular.contains(catalog.draw(List.of(), random).code())) {
                hits++;
            }
        }

        double expected = popularWeight / totalWeight;
        double observed = (double) hits / trials;

        assertThat(observed).isBetween(expected * 0.6, expected * 1.4);

        // 인기 지역 1곳이 일반 지역 1곳보다 덜 뽑히는지를 1인당 비율로 다시 확인한다
        double perPopular = observed / popular.size();
        double perOther = (1 - observed) / (catalog.size() - popular.size());

        assertThat(perPopular).isLessThan(perOther);
    }

    /* ── 시딩 누락 ───────────────────────────────────────── */

    @Test
    @DisplayName("추첨 - 시딩 전(목록 0건)이면 IllegalStateException (설정 실수라 500 계열)")
    void draw_emptyCatalog() {
        // 목 ObjectMapper 는 readTree 에서 null 을 돌려주고, load() 가 이를 삼켜 빈 목록이 된다.
        // = regions.json 이 없거나 깨진 상황과 같은 상태
        RegionCatalog empty = new RegionCatalog(mock(ObjectMapper.class));

        assertThat(empty.size()).isZero();
        assertThatThrownBy(() -> empty.draw(List.of(), new Random(1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
