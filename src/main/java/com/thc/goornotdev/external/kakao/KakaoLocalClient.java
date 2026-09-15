package com.thc.goornotdev.external.kakao;

import com.thc.goornotdev.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 카카오 로컬 API 클라이언트.
 *
 * 지금 쓰는 기능은 "주소 → 좌표" 하나뿐이다.
 * TourAPI 의 시군구 목록에는 좌표가 없어서, 시군구명을 좌표로 바꾸는 용도로만 쓴다.
 * (지도 렌더링은 프론트의 카카오맵 JS SDK 가 별도 키로 처리한다)
 *
 * 주의 - 인증키 취급:
 * TourAPI 와 달리 카카오는 키를 쿼리스트링이 아니라 Authorization 헤더로 보낸다.
 * 그래서 URI 에는 키가 섞이지 않지만, 하위 예외 메시지에 헤더가 딸려 나올 가능성까지
 * 막으려고 로그·예외로 나가는 문자열은 모두 {@link #mask(String)} 를 거친다.
 */
@Slf4j
@Component
public class KakaoLocalClient {
    /** KakaoAK 뒤에 붙는 키 값을 가리기 위한 패턴 (요청 헤더가 예외에 딸려 나오는 경우) */
    private static final Pattern KEY_PATTERN = Pattern.compile("(?i)(KakaoAK\\s+)\\S+");

    /**
     * 카카오가 오류 본문에 키를 되돌려주는 형태를 가리기 위한 패턴.
     *
     * 키가 틀리면 카카오는 401 과 함께
     * {@code {"errorType":"AccessDeniedError","message":"wrong appKey(...) format"}} 를 내려준다.
     * 즉 우리가 보낸 키가 응답 본문에 그대로 담겨 돌아온다. 이걸 그대로 예외 메시지에 붙이면
     * 키가 로그와 502 응답으로 새어 나간다.
     */
    private static final Pattern APP_KEY_PATTERN = Pattern.compile("(?i)(appKey\\()[^)]*(\\))");

    /** 이보다 짧은 값은 키로 보지 않는다. 짧은 문자열을 통째로 치환하면 메시지가 망가진다 */
    private static final int MIN_MASKABLE_KEY_LENGTH = 8;

    private final KakaoLocalProperties kakaoLocalProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public KakaoLocalClient(KakaoLocalProperties kakaoLocalProperties, ObjectMapper objectMapper) {
        this.kakaoLocalProperties = kakaoLocalProperties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 주소 → 좌표.
     *
     * @param address 검색할 주소 (예: "서울특별시 종로구")
     * @return 첫 번째 매칭 결과. 매칭이 하나도 없으면 null (호출부가 건너뛸지 판단한다)
     */
    public KakaoLocalDto.Coordinate geocode(String address) {
        if (address == null || address.isBlank()) {
            throw new ExternalApiException("변환할 주소가 비어 있습니다. (kakao geocode)");
        }

        JsonNode root = parse(call(address));
        JsonNode documents = root.path("documents");

        if (!documents.isArray() || documents.isEmpty()) {
            return null;
        }

        JsonNode first = documents.get(0);

        return KakaoLocalDto.Coordinate.builder()
                .addressName(text(first, "address_name"))
                .bCode(bCode(first))
                .lng(coordinate(first, "x"))
                .lat(coordinate(first, "y"))
                .build();
    }

    /* ── 내부 ────────────────────────────────────────────── */

    private String call(String address) {
        // 키가 헤더에 있어 URI 재인코딩 문제가 없다. 한글 질의는 여기서 한 번만 인코딩된다
        URI uri = UriComponentsBuilder
                .fromUriString(kakaoLocalProperties.getBaseUrl())
                .pathSegment("search", "address.json")
                .queryParam("query", address)
                .queryParam("size", 1)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();

        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, kakaoLocalProperties.authorization())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        String rawBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

                        if (response.getStatusCode().isError()) {
                            // 401 은 키 문제, 429 는 쿼터 초과다. 상태 코드를 남겨야 원인을 구분할 수 있다.
                            // 자르기 전에 먼저 가린다. 순서가 반대면 200자 경계에 키 앞부분만 잘려 남는다
                            throw new ExternalApiException("카카오 로컬 API 오류 : HTTP "
                                    + response.getStatusCode().value() + " " + summary(maskSecrets(rawBody)));
                        }

                        return rawBody;
                    });
        } catch (ExternalApiException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("카카오 로컬 API 통신 실패 : {}", maskSecrets(e.getMessage()));

            throw new ExternalApiException("카카오 로컬 API 와 통신하지 못했습니다.");
        }
    }

    private JsonNode parse(String rawBody) {
        try {
            return objectMapper.readTree(rawBody);
        } catch (RuntimeException e) {
            throw new ExternalApiException("카카오 로컬 API 응답을 해석할 수 없습니다.");
        }
    }

    /** 오류 본문은 통째로 내보내지 않고 앞부분만 잘라 쓴다 */
    private String summary(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return "";
        }

        return rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody;
    }

    /**
     * 법정동 코드 추출.
     *
     * b_code 는 최상위가 아니라 address / road_address 객체 안에 들어 있다.
     * 지번 주소(address)에 먼저 들어오고, 도로명만 매칭된 경우엔 road_address 쪽에 있다.
     */
    private String bCode(JsonNode document) {
        String value = text(document.path("address"), "b_code");

        return (value != null) ? value : text(document.path("road_address"), "b_code");
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.get(name);

        return (value == null || value.isNull()) ? null : value.asString();
    }

    /** 카카오는 좌표도 문자열로 내려준다 */
    private Double coordinate(JsonNode node, String name) {
        String value = text(node, name);

        try {
            return (value == null || value.isBlank()) ? null : Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 형태를 아는 키 노출을 가린다. 설정과 무관하게 동작해서 테스트하기 쉽도록 static 으로 둔다.
     *   - "KakaoAK {key}"  : 우리가 보낸 헤더가 예외에 딸려 나온 경우
     *   - "appKey({key})"  : 카카오가 오류 본문에 되돌려준 경우
     */
    static String mask(String text) {
        if (text == null) {
            return null;
        }

        String masked = KEY_PATTERN.matcher(text).replaceAll("$1****");

        return APP_KEY_PATTERN.matcher(masked).replaceAll("$1****$2");
    }

    /**
     * 형태와 무관하게, 실제 키 값 자체를 문자열에서 지운다.
     *
     * 패턴 기반 {@link #mask(String)} 만으로는 카카오가 앞으로 키를 또 다른 형태로 되돌려줄 때
     * 다시 뚫린다. 그래서 "우리가 들고 있는 키 문자열이 들어 있으면 무조건 가린다" 를 마지막 방어선으로 둔다.
     * 로그·예외로 나가는 모든 문자열은 이 메서드를 거쳐야 한다.
     */
    private String maskSecrets(String text) {
        String masked = mask(text);

        if (masked == null) {
            return null;
        }

        String key = secretKey();

        return (key == null) ? masked : masked.replace(key, "****");
    }

    /**
     * 설정된 REST API 키 값.
     * authorization() 이 "KakaoAK {key}" 를 만들므로 거기서 역으로 떼어낸다.
     * (필드를 직접 읽지 않는 이유 : 테스트가 authorization() 만 오버라이드해도 마스킹이 따라오게 하려고)
     */
    private String secretKey() {
        String authorization;

        try {
            authorization = kakaoLocalProperties.authorization();
        } catch (RuntimeException e) {
            // 마스킹하려다 예외를 던져 원래 오류를 덮어버리면 안 된다
            return null;
        }

        if (authorization == null || authorization.isBlank()) {
            return null;
        }

        int space = authorization.indexOf(' ');
        String key = (space < 0) ? authorization : authorization.substring(space + 1);

        key = key.trim();

        return (key.length() < MIN_MASKABLE_KEY_LENGTH) ? null : key;
    }
}
