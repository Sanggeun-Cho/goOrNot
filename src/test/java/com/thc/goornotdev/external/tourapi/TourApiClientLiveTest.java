package com.thc.goornotdev.external.tourapi;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.thc.goornotdev.exception.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 TourAPI 를 호출하는 검증용 테스트.
 *
 * 기본 실행(./gradlew test)에서는 꺼져 있다.
 * - 개발계정 트래픽 한도가 1,000건/일 이라 매 빌드마다 호출하면 한도를 갉아먹는다
 * - 외부 네트워크에 의존해 CI 에서 불안정해진다
 *
 * 켜는 방법 : ./gradlew test -Dtourapi.live=true
 */
@EnabledIfSystemProperty(named = "tourapi.live", matches = "true")
@SpringBootTest
class TourApiClientLiveTest {

    @Autowired
    private TourApiClient tourApiClient;

    @Autowired
    private TourApiProperties tourApiProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /** 관광지(12) 목록을 서울(areaCode 1) 기준으로 조회 */
    private TourApiDto.ListResult list() {
        return tourApiClient.areaBasedList(10, 1, "A", "12", "1");
    }

    @Test
    @DisplayName("시나리오 1 - areaBasedList2 로 목록이 1건 이상 조회된다")
    void scenario1_areaBasedList() {
        TourApiDto.ListResult result = list();

        assertThat(result.getItems()).isNotEmpty();
        assertThat(result.getTotalCount()).isNotNull();
        assertThat(result.getItems().get(0).getContentId()).isNotBlank();
        assertThat(result.getItems().get(0).getTitle()).isNotBlank();

        System.out.println("[시나리오 1] totalCount=" + result.getTotalCount()
                + ", 조회 건수=" + result.getItems().size()
                + ", 첫 건=" + result.getItems().get(0).getTitle());
    }

    @Test
    @DisplayName("시나리오 2 - detailCommon2 로 상세가 파싱된다 (결과 1건 = 객체 응답 경로)")
    void scenario2_detailCommon() {
        String contentId = list().getItems().get(0).getContentId();

        TourApiDto.Item detail = tourApiClient.detailCommon(contentId);

        assertThat(detail.getContentId()).isEqualTo(contentId);
        assertThat(detail.getTitle()).isNotBlank();

        System.out.println("[시나리오 2] contentId=" + detail.getContentId()
                + ", title=" + detail.getTitle()
                + ", addr1=" + detail.getAddr1()
                + ", overview 존재=" + (detail.getOverview() != null));
    }

    @Test
    @DisplayName("시나리오 3 - 잘못된 serviceKey 는 ExternalApiException 으로 잡힌다")
    void scenario3_invalidServiceKey() {
        TourApiClient brokenClient = new TourApiClient(invalidKeyProperties(), objectMapper);

        assertThatThrownBy(() -> brokenClient.areaBasedList(10, 1, "A", "12", "1"))
                .isInstanceOf(ExternalApiException.class)
                .satisfies(e -> System.out.println("[시나리오 3] " + e.getMessage()));
    }

    @Test
    @DisplayName("시나리오 4 - 실패 로그와 예외 메시지에 serviceKey 평문이 남지 않는다")
    void scenario4_serviceKeyNeverLogged() {
        Logger logger = (Logger) LoggerFactory.getLogger(TourApiClient.class);
        Level originalLevel = logger.getLevel();

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        // 호출 URI 를 찍는 debug 로그까지 포함해서 검사해야 의미가 있다
        logger.setLevel(Level.DEBUG);

        String realKey = tourApiProperties.getServiceKey();
        String invalidKey = invalidKeyProperties().getServiceKey();

        try {
            // 정상 호출 (진짜 키가 로그를 타는 경로)
            list();

            // 실패 호출 (가짜 키가 로그를 타는 경로)
            try {
                new TourApiClient(invalidKeyProperties(), objectMapper)
                        .areaBasedList(10, 1, "A", "12", "1");
            } catch (ExternalApiException e) {
                assertThat(e.getMessage()).doesNotContain(realKey);
                assertThat(e.getMessage()).doesNotContain(invalidKey);
            }

            List<String> logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();

            System.out.println("[시나리오 4] 수집한 로그 " + logs.size() + "건");
            logs.forEach(line -> System.out.println("  " + line));

            assertThat(logs).isNotEmpty();
            assertThat(logs).noneMatch(line -> line.contains(realKey));
            assertThat(logs).noneMatch(line -> line.contains(invalidKey));
            assertThat(logs).anyMatch(line -> line.contains("serviceKey=****"));
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    /**
     * 잘못된 키를 쓰는 설정.
     * TourApiProperties 는 @Value 로만 채워지므로 Setter 대신 게터를 덮어써 만든다.
     */
    private TourApiProperties invalidKeyProperties() {
        return new TourApiProperties() {
            @Override
            public String getServiceKey() {
                return "THIS-IS-AN-INVALID-SERVICE-KEY-FOR-TEST";
            }

            @Override
            public String getBaseUrl() {
                return tourApiProperties.getBaseUrl();
            }
        };
    }
}
