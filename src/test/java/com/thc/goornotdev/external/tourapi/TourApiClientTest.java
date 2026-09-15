package com.thc.goornotdev.external.tourapi;

import com.thc.goornotdev.exception.ExternalApiException;
import com.thc.goornotdev.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 네트워크 없이 응답 파싱만 검증한다.
 * 실제 TourAPI 호출은 TourApiClientLiveTest 에서 별도로 수행한다.
 */
class TourApiClientTest {

    private static final String OPERATION = "areaBasedList2";

    private final TourApiClient tourApiClient =
            new TourApiClient(new TourApiProperties(), JsonMapper.builder().build());

    private String success(String itemsJson) {
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":%s,"numOfRows":10,"pageNo":1,"totalCount":123}}}
                """.formatted(itemsJson);
    }

    @Test
    @DisplayName("파싱 - item 이 배열로 오는 일반적인 경우")
    void parseList_array() {
        String body = success("""
                {"item":[
                  {"contentid":"126508","title":"경복궁","addr1":"서울 종로구","mapx":"126.97","mapy":"37.57"},
                  {"contentid":"126509","title":"창덕궁","addr1":"서울 종로구","mapx":"126.99","mapy":"37.58"}
                ]}""");

        TourApiDto.ListResult result = tourApiClient.parseList(body, OPERATION);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getContentId()).isEqualTo("126508");
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("경복궁");
        assertThat(result.getItems().get(0).getMapX()).isEqualTo("126.97");
        assertThat(result.getTotalCount()).isEqualTo(123);
    }

    @Test
    @DisplayName("파싱 - 결과가 1건이면 item 이 배열이 아니라 객체로 온다")
    void parseList_singleObject() {
        String body = success("""
                {"item":{"contentid":"126508","title":"경복궁","addr1":"서울 종로구"}}""");

        TourApiDto.ListResult result = tourApiClient.parseList(body, OPERATION);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getContentId()).isEqualTo("126508");
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("파싱 - 결과가 0건이면 items 가 빈 문자열로 오기도 한다")
    void parseList_emptyItems() {
        TourApiDto.ListResult result = tourApiClient.parseList(success("\"\""), OPERATION);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("파싱 - 카멜 표기(contentId)로 내려와도 읽는다")
    void parseList_camelCaseKeys() {
        String body = success("""
                {"item":{"contentId":"126508","firstImage":"http://img","mapX":"126.97"}}""");

        TourApiDto.Item item = tourApiClient.parseList(body, OPERATION).getItems().get(0);

        assertThat(item.getContentId()).isEqualTo("126508");
        assertThat(item.getFirstImage()).isEqualTo("http://img");
        assertThat(item.getMapX()).isEqualTo("126.97");
    }

    @Test
    @DisplayName("오류 - resultCode 가 0000 이 아니면 ExternalApiException")
    void parseList_resultCodeError() {
        String body = """
                {"response":{"header":{"resultCode":"22",
                "resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},"body":{}}}""";

        assertThatThrownBy(() -> tourApiClient.parseList(body, OPERATION))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("22")
                .hasMessageContaining("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR");
    }

    @Test
    @DisplayName("오류 - 게이트웨이에서 걸러지면 response 봉투 없이 flat 으로 온다")
    void parseList_gatewayFlatError() {
        // 파라미터 오류·인증키 오류·트래픽 초과는 매뉴얼에 없는 이 구조로 내려온다
        String body = """
                {"responseTime":"2026-09-13T00:06:41.056","resultCode":"10",
                "resultMsg":"INVALID_REQUEST_PARAMETER_ERROR(arrangeType)"}""";

        assertThatThrownBy(() -> tourApiClient.parseList(body, OPERATION))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("10")
                .hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");
    }

    @Test
    @DisplayName("오류 - 사유를 알 수 없는 응답도 ExternalApiException 으로 떨어진다")
    void parseList_unknownEnvelope() {
        assertThatThrownBy(() -> tourApiClient.parseList("{\"foo\":\"bar\"}", OPERATION))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("알 수 없는 오류");
    }

    @Test
    @DisplayName("오류 - 인증 오류는 json 을 요청해도 XML 로 오는데 이것도 ExternalApiException")
    void parseList_xmlErrorEnvelope() {
        String body = """
                <OpenAPI_ServiceResponse><cmmMsgHeader>
                <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
                <returnReasonCode>30</returnReasonCode>
                </cmmMsgHeader></OpenAPI_ServiceResponse>""";

        assertThatThrownBy(() -> tourApiClient.parseList(body, OPERATION))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                .hasMessageContaining("30");
    }

    @Test
    @DisplayName("오류 - 본문이 비어 있으면 ExternalApiException")
    void parseList_emptyBody() {
        assertThatThrownBy(() -> tourApiClient.parseList("", OPERATION))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("마스킹 - URI 의 serviceKey 값이 가려진다")
    void mask_hidesServiceKeyInUri() {
        String uri = "https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
                + "?serviceKey=abcDEF123%2Bxyz%3D%3D&MobileOS=ETC&_type=json";

        String masked = TourApiClient.mask(uri);

        assertThat(masked).doesNotContain("abcDEF123");
        assertThat(masked).contains("serviceKey=****");
        // 뒤따르는 파라미터까지 지워버리면 안 된다
        assertThat(masked).contains("MobileOS=ETC");
    }

    @Test
    @DisplayName("마스킹 - 오류 응답 본문에 섞여 있어도 가려진다")
    void mask_hidesServiceKeyInMessage() {
        String message = "I/O error on GET request for \"https://apis.data.go.kr/x?serviceKey=SECRET_VALUE\"";

        assertThat(TourApiClient.mask(message)).doesNotContain("SECRET_VALUE");
    }

    @Test
    @DisplayName("예외 매핑 - ExternalApiException 은 502 Bad Gateway 로 내려간다")
    void handler_mapsToBadGateway() {
        // 컨트롤러를 만들지 않는 단계라 핸들러를 직접 호출해 매핑만 확인한다
        ResponseEntity<Map<String, String>> response = new GlobalExceptionHandler()
                .handleExternalApi(new ExternalApiException("관광정보 API 오류 (areaBasedList2) : 30"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).containsKey("error");
        assertThat(response.getBody().get("error")).contains("30");
    }
}
