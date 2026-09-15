package com.thc.goornotdev.external.tourapi;

import com.thc.goornotdev.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI 4.0 (KorService2) 호출 전용 클라이언트.
 *
 * 이 클래스 밖으로는 TourApiDto 만 나간다. 원본 JSON/XML 문자열은 노출하지 않는다.
 *
 * 주의 - 인증키 취급:
 * serviceKey 는 로그·예외 메시지·응답 어디에도 평문으로 남으면 안 된다.
 * 호출 URI 에는 반드시 serviceKey 가 들어가므로, URI 나 하위 예외 메시지를 그대로
 * 넘기지 않고 {@link #mask(String)} 를 거친 문자열만 사용한다.
 */
@Slf4j
@Component
public class TourApiClient {
    /** 공공데이터포털 공통 성공 코드 */
    private static final String SUCCESS_CODE = "0000";

    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "goOrNot";
    private static final String RESPONSE_TYPE = "json";

    private static final String OP_AREA_BASED_LIST = "areaBasedList2";
    private static final String OP_LOCATION_BASED_LIST = "locationBasedList2";
    private static final String OP_DETAIL_COMMON = "detailCommon2";
    private static final String OP_LDONG_CODE = "ldongCode2";

    /** serviceKey=... 부분만 골라내 가리기 위한 패턴 */
    private static final Pattern SERVICE_KEY_PATTERN = Pattern.compile("(?i)(serviceKey=)[^&\\s\"<]*");

    /** GW 인증 오류는 _type=json 을 보내도 XML 로 내려온다. 그때 사유를 뽑아내기 위한 패턴 */
    private static final Pattern XML_REASON_PATTERN =
            Pattern.compile("<(returnAuthMsg|errMsg|returnReasonCode|resultMsg|resultCode)>(.*?)</\\1>", Pattern.DOTALL);

    private final TourApiProperties tourApiProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TourApiClient(TourApiProperties tourApiProperties, ObjectMapper objectMapper) {
        this.tourApiProperties = tourApiProperties;
        this.objectMapper = objectMapper;

        // 외부 API 가 느릴 때 우리 스레드가 무한정 잡혀 있지 않도록 타임아웃을 명시한다
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 지역기반 목록 조회.
     *
     * @param arrange  정렬. A(제목순) C(수정일순) D(생성일순), 대표이미지 보장 버전은 O/Q/R.
     *                 선택 파라미터라 null 이면 보내지 않는다
     * @param areaCode 지역코드. null 이면 파라미터를 아예 보내지 않아 전국 조회가 된다
     */
    public TourApiDto.ListResult areaBasedList(int numOfRows, int pageNo, String arrange,
                                               String contentTypeId, String areaCode) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        // 정렬 파라미터 이름은 arrangeType 이 아니라 arrange 다.
        // arrangeType 으로 보내면 INVALID_REQUEST_PARAMETER_ERROR(10) 가 난다 (활용매뉴얼 v4.4 기준)
        params.put("arrange", arrange);
        params.put("contentTypeId", contentTypeId);
        params.put("areaCode", areaCode);

        return parseList(call(OP_AREA_BASED_LIST, params), OP_AREA_BASED_LIST);
    }

    /**
     * 위치기반 목록 조회. 확정된 지역 좌표를 중심으로 주변 장소를 찾는다.
     *
     * @param mapX   경도 (TourAPI 는 X 가 경도다. 위경도 순서를 뒤집으면 엉뚱한 곳이 나온다)
     * @param mapY   위도
     * @param radius 반경(m). TourAPI 최대값은 20,000
     */
    public TourApiDto.ListResult locationBasedList(double mapX, double mapY, int radius, int numOfRows, int pageNo,
                                                   String arrange, String contentTypeId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(numOfRows));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("mapX", String.valueOf(mapX));
        params.put("mapY", String.valueOf(mapY));
        params.put("radius", String.valueOf(radius));
        params.put("arrange", arrange);
        params.put("contentTypeId", contentTypeId);

        return parseList(call(OP_LOCATION_BASED_LIST, params), OP_LOCATION_BASED_LIST);
    }

    /**
     * 법정동 코드 조회.
     *
     * 파라미터 없이 부르면 시도 목록(코드 2자리, 세종만 5자리)이 오고,
     * lDongRegnCd 에 시도 코드를 주면 그 시도의 시군구 목록(코드 3자리)이 온다.
     * 시군구 전체 코드는 두 값을 이어붙인 형태다 (예: 11 + 110 = 11110 종로구).
     *
     * @param lDongRegnCd 시도 코드. null 이면 시도 목록
     */
    public List<TourApiDto.Code> ldongCode(String lDongRegnCd) {
        Map<String, String> params = new LinkedHashMap<>();
        // 시군구는 가장 많은 시도(경기도)도 50개 미만이라 한 페이지면 충분하다
        params.put("numOfRows", "100");
        params.put("pageNo", "1");
        params.put("lDongRegnCd", lDongRegnCd);

        return parseCodes(call(OP_LDONG_CODE, params), OP_LDONG_CODE);
    }

    /**
     * 공통정보 상세 조회. 결과가 없으면 null 이 아니라 예외로 알린다.
     */
    public TourApiDto.Item detailCommon(String contentId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "1");
        params.put("pageNo", "1");
        params.put("contentId", contentId);

        List<TourApiDto.Item> items = parseList(call(OP_DETAIL_COMMON, params), OP_DETAIL_COMMON).getItems();

        if (items.isEmpty()) {
            throw new ExternalApiException("상세 정보가 없습니다. (" + OP_DETAIL_COMMON + ", contentId : " + contentId + ")");
        }

        return items.get(0);
    }

    /**
     * 실제 HTTP 호출. 응답 본문을 문자열 그대로 돌려준다.
     *
     * RestClient 의 기본 에러 처리는 예외 메시지에 요청 URI 를 그대로 담을 수 있어 인증키가 샌다.
     * 그래서 exchange 로 상태코드와 본문을 직접 받아 우리가 만든 메시지만 밖으로 내보낸다.
     */
    private String call(String operation, Map<String, String> params) {
        URI uri = buildUri(operation, params);

        log.debug("TourAPI 호출 : {}", mask(uri.toString()));

        try {
            return restClient.get()
                    .uri(uri)
                    // 인증 오류일 때는 XML 로 내려오므로 둘 다 받는다고 알린다
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
                    .exchange((request, response) -> {
                        String rawBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);

                        if (response.getStatusCode().isError()) {
                            throw new ExternalApiException("HTTP " + response.getStatusCode().value()
                                    + " (" + operation + ")");
                        }

                        return rawBody;
                    });
        } catch (ExternalApiException e) {
            throw e;
        } catch (RestClientException e) {
            // 타임아웃·DNS 실패 등. 이 예외의 메시지에는 요청 URI(=serviceKey)가 들어있으므로
            // 원인 예외를 붙이지 않고, 마스킹한 메시지만 남긴다
            log.warn("TourAPI 통신 실패 ({}) : {}", operation, mask(e.getMessage()));

            throw new ExternalApiException("관광정보 서버와 통신하지 못했습니다. (" + operation + ")");
        }
    }

    /**
     * 요청 URI 조립.
     *
     * serviceKey 이중 인코딩 주의:
     * application.yml 에 넣는 값은 공공데이터포털의 "인코딩(Encoding)" 형태라 이미 %2B, %3D 등이 들어있다.
     * 여기서 한 번 더 인코딩되면 %2B 가 %252B 로 바뀌어
     * SERVICE_KEY_IS_NOT_REGISTERED_ERROR(30) 가 발생한다. 그래서
     *   1) build(true) 로 "구성요소가 이미 인코딩된 상태" 임을 알려 재인코딩을 막고
     *   2) RestClient 에 String 이 아닌 URI 를 넘겨 UriBuilderFactory 가 다시 인코딩하지 못하게 한다
     * 두 가지를 함께 지킨다. (둘 중 하나만 해서는 막히지 않는다)
     */
    private URI buildUri(String operation, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(tourApiProperties.getBaseUrl())
                .pathSegment(operation)
                .queryParam("serviceKey", tourApiProperties.getServiceKey())
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", RESPONSE_TYPE);

        // null 인 선택 파라미터는 아예 보내지 않는다 (빈 값을 보내면 파라미터 오류가 날 수 있다)
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.queryParam(key, value);
            }
        });

        return builder.build(true).toUri();
    }

    /**
     * 응답 본문 → ListResult.
     * 테스트에서 직접 호출할 수 있도록 package-private 로 열어둔다 (HTTP 없이 파싱만 검증).
     */
    TourApiDto.ListResult parseList(String rawBody, String operation) {
        JsonNode body = verifyAndGetBody(rawBody, operation);

        return TourApiDto.ListResult.builder()
                .items(toItems(body.path("items")))
                .numOfRows(intOrNull(body, "numOfRows"))
                .pageNo(intOrNull(body, "pageNo"))
                .totalCount(intOrNull(body, "totalCount"))
                .build();
    }

    /**
     * 공통 응답 규격 검증 후 response.body 반환.
     *
     * 봉투(envelope)가 두 종류로 온다.
     *
     * 1) 게이트웨이 단계에서 걸러진 요청 — 파라미터 오류, 인증키 오류, 트래픽 초과 등.
     *    매뉴얼에 없는 flat 구조다.
     *    {"responseTime":"...", "resultCode":"10", "resultMsg":"INVALID_REQUEST_PARAMETER_ERROR(...)"}
     *
     * 2) TourAPI 백엔드까지 도달한 요청 — 성공이든 업무 레벨 오류든 매뉴얼대로 온다.
     *    {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},"body":{...}}}
     *
     * 최상위에 response 키가 있는지로 둘을 구분한다.
     */
    private JsonNode verifyAndGetBody(String rawBody, String operation) {
        String trimmed = (rawBody == null) ? "" : rawBody.trim();

        if (trimmed.isEmpty()) {
            throw new ExternalApiException("응답이 비어 있습니다. (" + operation + ")");
        }

        // 인증 계열 오류(20/30/31)는 _type=json 을 보내도 XML 로 내려온다
        if (trimmed.startsWith("<")) {
            throw new ExternalApiException("관광정보 API 오류 (" + operation + ") : " + mask(xmlReason(trimmed)));
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(trimmed);
        } catch (RuntimeException e) {
            // 파싱 실패 메시지에 본문 일부가 섞여 나갈 수 있어 원인은 붙이지 않는다
            throw new ExternalApiException("응답을 해석할 수 없습니다. (" + operation + ")");
        }

        JsonNode response = root.path("response");

        // 1) 게이트웨이 레벨 오류. 여기는 애초에 성공으로 내려오지 않는다
        if (response.isMissingNode()) {
            throw new ExternalApiException("관광정보 API 오류 (" + operation + ") : " + mask(reason(root)));
        }

        // 2) 매뉴얼 구조. 성공 코드는 4자리 "0000"
        JsonNode header = response.path("header");

        if (!SUCCESS_CODE.equals(text(header, "resultCode"))) {
            throw new ExternalApiException("관광정보 API 오류 (" + operation + ") : " + mask(reason(header)));
        }

        return response.path("body");
    }

    /** resultCode + resultMsg 를 사람이 읽을 한 줄로. 둘 다 없으면 원문을 붙이지 않고 뭉뚱그린다 */
    private String reason(JsonNode node) {
        String resultCode = text(node, "resultCode");
        String resultMsg = text(node, "resultMsg");

        if (resultCode == null && resultMsg == null) {
            return "알 수 없는 오류";
        }

        return ((resultCode == null ? "" : resultCode) + " " + (resultMsg == null ? "" : resultMsg)).trim();
    }

    /**
     * 코드 목록 응답(ldongCode2 등) 파싱.
     * 테스트에서 직접 호출할 수 있도록 package-private.
     */
    List<TourApiDto.Code> parseCodes(String rawBody, String operation) {
        List<TourApiDto.Code> result = new ArrayList<>();

        forEachItem(verifyAndGetBody(rawBody, operation).path("items"), node -> result.add(TourApiDto.Code.builder()
                .code(text(node, "code"))
                .name(text(node, "name"))
                .build()));

        return result;
    }

    private List<TourApiDto.Item> toItems(JsonNode items) {
        List<TourApiDto.Item> result = new ArrayList<>();

        forEachItem(items, node -> result.add(toItem(node)));

        return result;
    }

    /**
     * items 안의 item 을 하나씩 넘겨준다.
     *
     * 결과가 1건이면 item 이 배열이 아니라 객체로 오고,
     * 0건이면 items 자체가 빈 문자열("")로 오는 경우가 있어 세 가지를 모두 처리한다.
     * 이 분기를 한 곳에만 두려고 목록/코드 파싱이 공유한다.
     */
    private void forEachItem(JsonNode items, Consumer<JsonNode> consumer) {
        JsonNode item = items.path("item");

        if (item.isArray()) {
            item.forEach(consumer);
        } else if (item.isObject()) {
            consumer.accept(item);
        }
        // 그 외(없음 / 빈 문자열)는 결과 0건이므로 아무것도 넘기지 않는다
    }

    /**
     * JsonNode → Item.
     *
     * 필드명을 Jackson 자동 바인딩에 맡기지 않고 직접 꺼낸다.
     * TourAPI 는 전통적으로 전부 소문자 키(contentid, firstimage, mapx)를 쓰는데
     * KorService2 의 실제 표기를 원문 문서로 확정하지 못해, 소문자/카멜 표기를 모두 시도한다.
     */
    private TourApiDto.Item toItem(JsonNode node) {
        return TourApiDto.Item.builder()
                .contentId(text(node, "contentid", "contentId"))
                .contentTypeId(text(node, "contenttypeid", "contentTypeId"))
                .title(text(node, "title"))
                .addr1(text(node, "addr1"))
                .addr2(text(node, "addr2"))
                .areaCode(text(node, "areacode", "areaCode"))
                .sigunguCode(text(node, "sigungucode", "sigunguCode"))
                .cat1(text(node, "cat1"))
                .cat2(text(node, "cat2"))
                .cat3(text(node, "cat3"))
                .firstImage(text(node, "firstimage", "firstImage"))
                .firstImage2(text(node, "firstimage2", "firstImage2"))
                .mapX(text(node, "mapx", "mapX"))
                .mapY(text(node, "mapy", "mapY"))
                .tel(text(node, "tel"))
                .dist(text(node, "dist"))
                .overview(text(node, "overview"))
                .homepage(text(node, "homepage"))
                // 아래 3개는 매뉴얼에 없지만 실제 응답에 포함된다. 카멜 표기로 내려와 후보를 둘 다 둔다
                .cpyrhtDivCd(text(node, "cpyrhtdivcd", "cpyrhtDivCd"))
                .lDongRegnCd(text(node, "ldongregncd", "lDongRegnCd"))
                .lDongSignguCd(text(node, "ldongsignogucd", "lDongSignguCd"))
                .build();
    }

    /** 후보 이름들을 순서대로 찾아 첫 값을 반환. 없으면 null */
    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);

            if (value != null && !value.isNull()) {
                // Jackson 3 에서 asText() 가 asString() 으로 바뀌었다
                String result = value.asString();

                if (!result.isEmpty()) {
                    return result;
                }
            }
        }

        return null;
    }

    private Integer intOrNull(JsonNode node, String name) {
        String value = text(node, name);

        try {
            return (value == null) ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** XML 오류 응답에서 사람이 읽을 사유만 추려낸다 */
    private String xmlReason(String xml) {
        StringBuilder reason = new StringBuilder();
        Matcher matcher = XML_REASON_PATTERN.matcher(xml);

        while (matcher.find()) {
            if (!reason.isEmpty()) {
                reason.append(", ");
            }

            reason.append(matcher.group(1)).append("=").append(matcher.group(2).trim());
        }

        return reason.isEmpty() ? "알 수 없는 오류" : reason.toString();
    }

    /**
     * serviceKey 값을 가린다. 로그·예외 메시지로 나가는 모든 문자열은 이 함수를 거친다.
     * 테스트에서 직접 검증할 수 있도록 package-private.
     */
    static String mask(String text) {
        return (text == null) ? null : SERVICE_KEY_PATTERN.matcher(text).replaceAll("$1****");
    }
}
