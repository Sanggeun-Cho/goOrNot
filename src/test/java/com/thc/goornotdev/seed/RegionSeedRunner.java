package com.thc.goornotdev.seed;

import com.thc.goornotdev.external.kakao.KakaoLocalClient;
import com.thc.goornotdev.external.kakao.KakaoLocalDto;
import com.thc.goornotdev.external.tourapi.TourApiClient;
import com.thc.goornotdev.external.tourapi.TourApiDto;
import com.thc.goornotdev.util.RegionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * regions.json 시딩. 1회성 배치다.
 *
 * 실행 방법 : ./gradlew test --tests '*RegionSeedRunner' -Dregion.seed=true
 *
 * 왜 테스트에 있나:
 * 애플리케이션이 뜰 때마다 돌면 외부 API 쿼터를 계속 갉아먹는다. 실제 서비스 로직은
 * 이 파일을 읽기만 하고, 생성은 사람이 필요할 때 한 번만 돌린다.
 *
 * 이어서 돌리기 (중요):
 * 카카오 호출이 시군구 수(약 230건)만큼 나가므로, 중간에 끊겨도 처음부터 다시 돌리면 낭비다.
 * 그래서 기존 regions.json 을 먼저 읽어 <b>이미 있는 시군구는 건드리지 않고</b> 빠진 것만 채운다.
 * 덕분에 손으로 조정한 weight 도 재실행에서 그대로 보존된다.
 *
 * 산출물은 build 가 아니라 소스 트리({@link #OUTPUT_PATH})에 쓴다.
 * 빌드 산출물에 쓰면 clean 한 번에 사라진다.
 */
@EnabledIfSystemProperty(named = "region.seed", matches = "true")
@SpringBootTest
class RegionSeedRunner {
    private static final Path OUTPUT_PATH = Path.of("src", "main", "resources", RegionCatalog.RESOURCE_PATH);

    /**
     * 인기 지역 큐레이션 (= 낮은 weight).
     *
     * 코드가 아니라 (시도 키워드, 시군구명 앞부분) 으로 적는다.
     * 행정구역 개편으로 코드·명칭이 바뀌어도(강원도 → 강원특별자치도 등) 매칭이 깨지지 않게 하려는 것이다.
     * 시군구명은 startsWith 로 비교해서 "수원시 장안구" 같은 하위 구까지 함께 걸린다.
     *
     * 기획안 기준 : 완전 배제가 아니라 확률 차등이라 인기 지역도 낮은 확률로 뽑힌다.
     */
    private static final List<String[]> POPULAR = List.of(
            // 서울 - 관광·상권 집중
            new String[]{"서울", "종로구"},
            new String[]{"서울", "중구"},
            new String[]{"서울", "용산구"},
            new String[]{"서울", "마포구"},
            new String[]{"서울", "영등포구"},
            new String[]{"서울", "강남구"},
            new String[]{"서울", "서초구"},
            new String[]{"서울", "송파구"},
            // 부산
            new String[]{"부산", "중구"},
            new String[]{"부산", "해운대구"},
            new String[]{"부산", "수영구"},
            // 인천 - 개편 후 기준. 옛 중구가 제물포구/영종구로 나뉘었다
            new String[]{"인천", "제물포구"},
            new String[]{"인천", "영종구"},
            // 강원 - 동해안 관광지
            new String[]{"강원", "춘천시"},
            new String[]{"강원", "강릉시"},
            new String[]{"강원", "속초시"},
            new String[]{"강원", "평창군"},
            // 제주
            new String[]{"제주", "제주시"},
            new String[]{"제주", "서귀포시"},
            // 경기
            new String[]{"경기", "가평군"},
            new String[]{"경기", "파주시"},
            new String[]{"경기", "용인시"},
            // 경상
            new String[]{"경상북도", "경주시"},
            new String[]{"경상남도", "통영시"},
            new String[]{"경상남도", "거제시"},
            // 전라 - 광주와 전남이 "전남광주통합특별시" 로 합쳐져 시도명이 하나다
            new String[]{"전남광주", "여수시"},
            new String[]{"전남광주", "순천시"},
            new String[]{"전남광주", "동구"},
            new String[]{"전북", "전주시"},
            // 충청
            new String[]{"충청남도", "태안군"}
    );

    @Autowired
    private TourApiClient tourApiClient;

    @Autowired
    private KakaoLocalClient kakaoLocalClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("시딩 - 전국 시군구 목록과 좌표를 모아 regions.json 을 만든다 (이어서 실행 가능)")
    void seed() throws IOException {
        // code 순으로 정렬해 두면 파일 diff 가 안정적이라 손으로 관리하기 쉽다
        Map<String, Row> rows = new TreeMap<>(readExisting());
        int before = rows.size();

        System.out.println("[시딩] 기존 항목 " + before + "건. 빠진 시군구만 채웁니다.");

        List<String> failed = new ArrayList<>();
        int skipped = 0;
        int added = 0;

        for (TourApiDto.Code sido : tourApiClient.ldongCode(null)) {
            for (Target target : targetsOf(sido)) {
                if (rows.containsKey(target.code())) {
                    // 이미 있는 시군구. 카카오 호출을 아예 하지 않는다 (쿼터 절약 + 손으로 고친 weight 보존)
                    skipped++;
                    continue;
                }

                Row row = resolve(target);

                if (row == null) {
                    failed.add(target.code() + " " + target.fullName());
                    continue;
                }

                rows.put(row.code(), row);
                added++;

                System.out.println("  + " + row.code() + " " + row.name()
                        + " (" + row.lat() + ", " + row.lng() + ") weight=" + row.weight());
            }
        }

        int removed = removeSubDistricts(rows);

        write(rows);

        System.out.println("[시딩] 완료 - 전체 " + rows.size() + "건 (신규 " + added
                + " / 건너뜀 " + skipped + " / 하위 구 제외 " + removed
                + " / 실패 " + failed.size() + ")");

        if (!failed.isEmpty()) {
            // 실패해도 파일은 쓴다. 다음 실행에서 실패분만 다시 시도된다
            System.out.println("[시딩] 실패 목록 (다음 실행 때 자동 재시도) :");
            failed.forEach(item -> System.out.println("  - " + item));
        }

        System.out.println("[시딩] 산출물 : " + OUTPUT_PATH.toAbsolutePath());
    }

    /* ── 수집 ────────────────────────────────────────────── */

    /** 시도 하나에 대한 추첨 후보들 */
    private record Target(String code, String sidoName, String sigunguName) {
        String fullName() {
            return sigunguName.isBlank() ? sidoName : sidoName + " " + sigunguName;
        }
    }

    /**
     * 시도 → 후보 목록.
     *
     * 보통은 하위 시군구를 다시 조회한다. 다만 세종특별자치시는 시군구가 없고
     * 시도 코드 자체가 5자리(36110)로 내려와서, 그 자체를 후보 하나로 취급한다.
     */
    private List<Target> targetsOf(TourApiDto.Code sido) {
        List<Target> targets = new ArrayList<>();

        if (sido.getCode() != null && sido.getCode().length() >= 5) {
            targets.add(new Target(sido.getCode().substring(0, 5), sido.getName(), ""));

            return targets;
        }

        for (TourApiDto.Code sigungu : tourApiClient.ldongCode(sido.getCode())) {
            // 시군구 전체 코드 = 시도코드(2) + 시군구코드(3)
            targets.add(new Target(sido.getCode() + sigungu.getCode(), sido.getName(), sigungu.getName()));
        }

        // 하위 구는 카카오를 호출하기 "전에" 버린다.
        // 호출한 뒤에 버리면 매번 40건씩 쿼터만 태우고 결과는 같다
        return withoutSubDistricts(targets);
    }

    /**
     * 일반구(수원시 장안구 등)를 후보에서 뺀다. {@link #removeSubDistricts} 와 같은 규칙이다.
     * 이쪽은 수집 단계, 저쪽은 이미 만들어진 파일 정리용이다.
     */
    private List<Target> withoutSubDistricts(List<Target> targets) {
        Map<String, Target> byCode = new LinkedHashMap<>();

        targets.forEach(target -> byCode.put(target.code(), target));

        List<Target> kept = new ArrayList<>();

        for (Target target : targets) {
            Target parent = byCode.get(target.code().substring(0, 4) + "0");

            boolean subDistrict = !target.code().endsWith("0")
                    && parent != null
                    && !parent.code().equals(target.code())
                    && target.sigunguName().startsWith(parent.sigunguName());

            if (!subDistrict) {
                kept.add(target);
            }
        }

        return kept;
    }

    /**
     * 후보 하나를 좌표까지 확정한다. 확정 못 하면 null.
     *
     * 정확성 검증 (중요):
     * "중구" 처럼 여러 시도에 같은 이름이 있어서, 질의에는 항상 시도명을 붙이고
     * 응답의 법정동 코드 앞 5자리가 기대한 시군구 코드와 <b>정확히 일치할 때만</b> 채택한다.
     * address_name 문자열 비교는 쓸 수 없다 ("서울특별시 종로구" 로 물어도 "서울 종로구" 로 답한다).
     */
    private Row resolve(Target target) {
        try {
            KakaoLocalDto.Coordinate coordinate = kakaoLocalClient.geocode(target.fullName());

            if (coordinate == null || coordinate.getLat() == null || coordinate.getLng() == null) {
                System.out.println("  ! 매칭 없음 : " + target.fullName());

                return null;
            }

            if (!matches(target, coordinate)) {
                System.out.println("  ! 코드 불일치 : " + target.fullName()
                        + " 기대=" + target.code() + " 실제=" + coordinate.sigunguCode());

                return null;
            }

            return new Row(target.code(), target.fullName(), target.sidoName(), target.sigunguName(),
                    coordinate.getLat(), coordinate.getLng(), weightOf(target));
        } catch (RuntimeException e) {
            // 한 건 실패로 전체를 멈추지 않는다. 실패분은 다음 실행에서 다시 시도된다
            System.out.println("  ! 실패 : " + target.fullName() + " - " + e.getMessage());

            return null;
        }
    }

    /**
     * 좌표가 기대한 지역의 것이 맞는지 판정.
     *
     * 기본은 시군구 코드 5자리 완전 일치다. 다만 세종특별자치시처럼 하위 시군구가 없는 곳은
     * TourAPI 가 36110 을 주는데 카카오 법정동 코드는 시도 레벨인 36000 으로 내려온다.
     * 이런 시도 단위 후보는 앞 2자리(시도)만 맞으면 같은 지역으로 본다.
     */
    private boolean matches(Target target, KakaoLocalDto.Coordinate coordinate) {
        String actual = coordinate.sigunguCode();

        if (actual == null) {
            return false;
        }

        if (target.sigunguName().isBlank()) {
            return actual.startsWith(target.code().substring(0, 2));
        }

        return target.code().equals(actual);
    }

    private double weightOf(Target target) {
        for (String[] popular : POPULAR) {
            if (target.sidoName() != null && target.sidoName().contains(popular[0])
                    && target.sigunguName() != null && target.sigunguName().startsWith(popular[1])) {
                return RegionCatalog.WEIGHT_POPULAR;
            }
        }

        return RegionCatalog.WEIGHT_DEFAULT;
    }

    /**
     * 일반구(수원시 장안구 등)를 후보에서 제외하고 시 단위만 남긴다.
     *
     * 왜 필요한가 (중요):
     * ldongCode2 는 "수원시(41110)" 와 그 하위 "장안구/권선구/팔달구/영통구" 를 <b>모두</b> 내려준다.
     * 둘 다 후보로 두면 수원 지역이 5장의 복권을 쥐는 셈이라 대도시일수록 당첨 확률이 올라간다.
     * 소외 지역을 밀어주자는 기획 의도와 정반대라서, 시 단위 하나로 합친다.
     *
     * 판별 규칙 (코드 + 이름 둘 다 봐야 한다):
     * 일반구 코드는 상위 시 코드와 앞 4자리를 공유하고 끝자리가 0 이 아니다.
     *   41110 수원시 → 41111 장안구 / 41113 권선구 / 41115 팔달구 / 41117 영통구
     * 그런데 코드만 보면 오판이 난다. 43740 영동군과 43745 증평군은 앞 4자리가 같지만
     * 둘은 나란한 군이지 부모-자식이 아니다. 그래서 <b>이름이 상위 이름으로 시작할 때만</b>
     * 하위 구로 인정한다 ("수원시 장안구" 는 "수원시" 로 시작하지만 "증평군" 은 "영동군" 으로 시작하지 않는다).
     *
     * 상위 시가 목록에 실제로 존재할 때만 지운다. 인천 제물포구(28125)처럼 상위 시가 없는
     * 자치구는 그대로 후보로 남는다.
     *
     * @return 제외한 개수
     */
    private int removeSubDistricts(Map<String, Row> rows) {
        List<String> targets = new ArrayList<>();

        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            String code = entry.getKey();

            if (code.length() != 5 || code.endsWith("0")) {
                continue;
            }

            Row parent = rows.get(code.substring(0, 4) + "0");

            if (parent != null && isSubDistrictOf(entry.getValue(), parent)) {
                targets.add(code);
            }
        }

        targets.forEach(code -> {
            System.out.println("  - 하위 구 제외 : " + code + " " + rows.get(code).name()
                    + " (상위 " + code.substring(0, 4) + "0 으로 대표)");
            rows.remove(code);
        });

        return targets.size();
    }

    /** "수원시 장안구" 는 "수원시" 의 하위. "증평군" 은 "영동군" 의 하위가 아니다 */
    private boolean isSubDistrictOf(Row child, Row parent) {
        return child.sigunguName() != null
                && parent.sigunguName() != null
                && !parent.sigunguName().isBlank()
                && child.sigunguName().startsWith(parent.sigunguName());
    }

    /* ── 파일 입출력 ─────────────────────────────────────── */

    private record Row(String code, String name, String sidoName, String sigunguName,
                       Double lat, Double lng, Double weight) {
    }

    /**
     * 기존 파일을 그대로 읽어온다. 없으면 빈 맵.
     *
     * -Dregion.seed.reset=true 를 주면 기존 파일을 무시하고 처음부터 다시 만든다.
     * 큐레이션 기준(POPULAR)이나 수집 규칙을 바꿔서 weight 를 전부 다시 계산해야 할 때만 쓴다.
     * 손으로 조정한 weight 가 날아가므로 평소에는 쓰지 않는다.
     */
    private Map<String, Row> readExisting() {
        Map<String, Row> rows = new LinkedHashMap<>();

        if ("true".equals(System.getProperty("region.seed.reset"))) {
            System.out.println("[시딩] reset 모드 - 기존 파일을 무시하고 전부 다시 수집합니다.");

            return rows;
        }

        if (!Files.exists(OUTPUT_PATH)) {
            return rows;
        }

        try {
            JsonNode items = objectMapper
                    .readTree(Files.readString(OUTPUT_PATH, StandardCharsets.UTF_8))
                    .path("regions");

            for (JsonNode node : items) {
                Row row = new Row(
                        node.path("code").asString(),
                        node.path("name").asString(),
                        node.path("sidoName").asString(),
                        node.path("sigunguName").asString(),
                        node.path("lat").asDouble(),
                        node.path("lng").asDouble(),
                        node.path("weight").asDouble());

                rows.put(row.code(), row);
            }
        } catch (Exception e) {
            // 깨진 파일을 신뢰해 덮어쓰면 기존 큐레이션이 날아간다. 멈추고 사람이 확인하게 한다
            throw new IllegalStateException(
                    "기존 " + OUTPUT_PATH + " 를 읽지 못했습니다. 파일을 확인하세요 : " + e.getMessage(), e);
        }

        return rows;
    }

    /**
     * 한 줄에 한 지역씩 쓴다.
     * weight 를 손으로 조정하는 파일이라 사람이 읽고 git diff 로 확인하기 좋은 형태를 택했다.
     */
    private void write(Map<String, Row> rows) throws IOException {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("  \"generatedAt\": \"").append(LocalDateTime.now()).append("\",\n");
        json.append("  \"count\": ").append(rows.size()).append(",\n");
        json.append("  \"weightGuide\": \"인기 지역 ")
                .append(RegionCatalog.WEIGHT_POPULAR)
                .append(" / 그 외 ")
                .append(RegionCatalog.WEIGHT_DEFAULT)
                .append(". 값이 클수록 잘 뽑힌다. 손으로 조정해도 재시딩에서 보존된다.\",\n");
        json.append("  \"regions\": [\n");

        int index = 0;

        for (Row row : rows.values()) {
            json.append("    {")
                    .append("\"code\": \"").append(escape(row.code())).append("\", ")
                    .append("\"name\": \"").append(escape(row.name())).append("\", ")
                    .append("\"sidoName\": \"").append(escape(row.sidoName())).append("\", ")
                    .append("\"sigunguName\": \"").append(escape(row.sigunguName())).append("\", ")
                    .append("\"lat\": ").append(row.lat()).append(", ")
                    .append("\"lng\": ").append(row.lng()).append(", ")
                    .append("\"weight\": ").append(row.weight())
                    .append("}");

            json.append(++index < rows.size() ? ",\n" : "\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        Files.createDirectories(OUTPUT_PATH.getParent());
        Files.writeString(OUTPUT_PATH, json.toString(), StandardCharsets.UTF_8);
    }

    private String escape(String text) {
        return (text == null) ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
