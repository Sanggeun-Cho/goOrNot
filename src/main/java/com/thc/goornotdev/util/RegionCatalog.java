package com.thc.goornotdev.util;

import com.thc.goornotdev.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 지역(시군구) 후보 목록 + 가중치 랜덤 추첨.
 *
 * 왜 Entity/Repository 가 아니라 리소스 파일인가:
 * 시군구 목록은 CRUD 대상이 아닌 정적 참조 데이터다. 행정구역이 바뀌는 일이 거의 없고,
 * 바뀌더라도 시딩을 다시 돌려 파일을 갱신하면 된다. 테이블로 만들면 조회 때마다 DB 를 타고
 * 마이그레이션 대상만 늘어난다. 그래서 {@code resources/regions.json} 을 시작 시 한 번 읽어
 * 메모리에 들고 있는다. (파일 생성은 test 의 RegionSeedRunner 가 1회성으로 담당)
 *
 * 가중치 의미 (기획안 : 소외 지역 밀어주기):
 * 인기 지역은 낮은 weight, 나머지는 높은 weight 를 준다. 완전 배제가 아니라 확률 차등이라
 * 인기 지역도 낮은 확률로 뽑힌다.
 *
 * 스레드 안전:
 * 생성자에서 목록을 만들고 불변으로 고정한다. 추첨은 읽기 전용이고 난수는
 * {@link ThreadLocalRandom} 을 써서 동시 요청이 서로를 막지 않는다.
 */
@Slf4j
@Component
public class RegionCatalog {
    /** 시딩 산출물. RegionSeedRunner 와 경로를 공유한다 */
    public static final String RESOURCE_PATH = "regions.json";

    /** 인기 지역 (덜 나오게) */
    public static final double WEIGHT_POPULAR = 0.2;

    /** 그 외 전 지역 (기본) */
    public static final double WEIGHT_DEFAULT = 1.0;

    /**
     * 시군구 한 곳.
     *
     * @param code        법정동 기준 시군구 코드 5자리 (예: 11110)
     * @param name        표시용 전체 이름 (예: 서울특별시 종로구)
     * @param sidoName    시도명
     * @param sigunguName 시군구명
     * @param lat         위도
     * @param lng         경도
     * @param weight      추첨 가중치. 클수록 잘 뽑힌다
     */
    public record Region(String code, String name, String sidoName, String sigunguName,
                         Double lat, Double lng, Double weight) {
    }

    private final List<Region> regions;
    private final Map<String, Region> byCode;

    public RegionCatalog(ObjectMapper objectMapper) {
        this.regions = load(objectMapper);
        this.byCode = index(this.regions);

        if (this.regions.isEmpty()) {
            // 여기서 예외를 던지면 애플리케이션이 아예 못 뜬다.
            // 시딩 전에도 나머지 기능은 개발할 수 있어야 하므로 경고만 남기고, 추첨 시점에 막는다
            log.warn("지역 목록이 비어 있습니다. {} 시딩을 먼저 실행하세요.", RESOURCE_PATH);
        } else {
            log.info("지역 목록 {}개 로드 완료 (가중치 합계 {})", this.regions.size(), totalWeight(Set.of()));
        }
    }

    /* ── 조회 ────────────────────────────────────────────── */

    public List<Region> regions() {
        return regions;
    }

    public int size() {
        return regions.size();
    }

    /** 코드로 찾기. 없으면 null */
    public Region find(String code) {
        return (code == null) ? null : byCode.get(code);
    }

    /* ── 추첨 ────────────────────────────────────────────── */

    /**
     * 가중치 랜덤 추첨.
     *
     * @param excludedCodes 이번 추첨에서 제외할 시군구 코드.
     *                      "말래(재던지기)" 일 때 같은 세션에서 이미 뽑힌 지역을 넘긴다.
     *                      제외 = weight 0 과 같은 효과다
     */
    public Region draw(Collection<String> excludedCodes) {
        return draw(excludedCodes, ThreadLocalRandom.current());
    }

    /**
     * 난수원을 직접 받는 추첨.
     * 테스트에서 시드 고정 Random 을 넣어 결과를 재현하려고 열어둔다.
     */
    public Region draw(Collection<String> excludedCodes, Random random) {
        if (regions.isEmpty()) {
            // 설정 실수(시딩 누락)지 사용자 잘못이 아니라서 400 이 아닌 500 계열로 올린다
            throw new IllegalStateException(
                    "지역 목록이 비어 있어 추첨할 수 없습니다. " + RESOURCE_PATH + " 시딩이 필요합니다.");
        }

        Set<String> excluded = (excludedCodes == null) ? Set.of() : new HashSet<>(excludedCodes);
        double total = totalWeight(excluded);

        if (total <= 0) {
            throw new InvalidRequestException("더 이상 뽑을 수 있는 지역이 없습니다.");
        }

        // [0, total) 구간의 난수 위치를 뽑고, 가중치를 누적하며 그 위치가 속한 구간의 지역을 고른다
        double point = random.nextDouble() * total;
        double cursor = 0;

        for (Region region : regions) {
            if (excluded.contains(region.code())) {
                continue;
            }

            cursor += weightOf(region);

            if (point < cursor) {
                return region;
            }
        }

        // 부동소수점 오차로 마지막 구간을 스쳐 지나갈 수 있다. 그때는 마지막 후보로 떨어뜨린다
        return lastCandidate(excluded);
    }

    /* ── 내부 ────────────────────────────────────────────── */

    private double totalWeight(Set<String> excluded) {
        double total = 0;

        for (Region region : regions) {
            if (!excluded.contains(region.code())) {
                total += weightOf(region);
            }
        }

        return total;
    }

    /** weight 가 비었거나 음수면 기본값으로 본다. 시딩 파일을 손으로 고치다 깨져도 추첨이 멈추지 않게 한다 */
    private double weightOf(Region region) {
        Double weight = region.weight();

        return (weight == null || weight <= 0) ? WEIGHT_DEFAULT : weight;
    }

    private Region lastCandidate(Set<String> excluded) {
        for (int i = regions.size() - 1; i >= 0; i--) {
            if (!excluded.contains(regions.get(i).code())) {
                return regions.get(i);
            }
        }

        throw new InvalidRequestException("더 이상 뽑을 수 있는 지역이 없습니다.");
    }

    private Map<String, Region> index(List<Region> list) {
        Map<String, Region> map = new LinkedHashMap<>();

        list.forEach(region -> map.put(region.code(), region));

        return Collections.unmodifiableMap(map);
    }

    /**
     * regions.json 로드.
     * 파일이 없거나 깨져 있어도 애플리케이션은 떠야 하므로 빈 목록으로 떨어뜨린다.
     */
    private List<Region> load(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);

        if (!resource.exists()) {
            return List.of();
        }

        try (InputStream in = resource.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode items = objectMapper.readTree(json).path("regions");

            if (!items.isArray()) {
                log.warn("{} 의 regions 가 배열이 아닙니다.", RESOURCE_PATH);

                return List.of();
            }

            List<Region> list = new java.util.ArrayList<>();

            for (JsonNode node : items) {
                Region region = toRegion(node);

                // 좌표 없는 지역은 추첨돼도 장소 조회를 못 한다. 아예 후보에서 뺀다
                if (region.code() != null && region.lat() != null && region.lng() != null) {
                    list.add(region);
                }
            }

            return Collections.unmodifiableList(list);
        } catch (Exception e) {
            log.warn("{} 를 읽지 못했습니다 : {}", RESOURCE_PATH, e.getMessage());

            return List.of();
        }
    }

    private Region toRegion(JsonNode node) {
        return new Region(
                text(node, "code"),
                text(node, "name"),
                text(node, "sidoName"),
                text(node, "sigunguName"),
                number(node, "lat"),
                number(node, "lng"),
                number(node, "weight"));
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);

        return (value == null || value.isNull()) ? null : value.asString();
    }

    private Double number(JsonNode node, String name) {
        JsonNode value = node.get(name);

        return (value == null || value.isNull() || !value.isNumber()) ? null : value.asDouble();
    }
}
