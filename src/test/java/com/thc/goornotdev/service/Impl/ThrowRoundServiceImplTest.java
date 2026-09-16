package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowRoundDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.domain.ThrowChoice;
import com.thc.goornotdev.domain.ThrowRound;
import com.thc.goornotdev.domain.ThrowStatus;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.ThrowRoundMapper;
import com.thc.goornotdev.repository.ThrowRoundRepository;
import com.thc.goornotdev.service.ThrowSessionService;
import com.thc.goornotdev.util.RegionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThrowRoundServiceImplTest {

    private static final String DEVICE_ID = "device-uuid-1";

    @Mock
    private ThrowRoundRepository throwRoundRepository;

    @Mock
    private ThrowRoundMapper throwRoundMapper;

    @Mock
    private ThrowSessionService throwSessionService;

    // 추첨은 regions.json 을 들고 있는 카탈로그에 위임한다. 여기서는 "무엇을 넘겼는지"만 검증한다
    @Mock
    private RegionCatalog regionCatalog;

    @InjectMocks
    private ThrowRoundServiceImpl throwRoundService;

    private RegionCatalog.Region region() {
        return new RegionCatalog.Region("42210", "강원특별자치도 속초시", "강원특별자치도", "속초시",
                38.2070, 128.5918, 0.2);
    }

    private ThrowSessionDto.DetailResDto session(ThrowStatus status) {
        return ThrowSessionDto.DetailResDto.builder()
                .id(1L)
                .deviceId(DEVICE_ID)
                .status(status)
                .build();
    }

    private static final String REGION_CODE = "42210";

    private ThrowRoundDto.CreateReqDto createReqDto() {
        return createReqDto(REGION_CODE);
    }

    private ThrowRoundDto.CreateReqDto createReqDto(String regionCode) {
        return ThrowRoundDto.CreateReqDto.builder()
                .throwSessionId(1L)
                .regionCode(regionCode)
                .lat(37.5)
                .lng(127.0)
                .choice(ThrowChoice.AGAIN)
                .build();
    }

    /**
     * 회차 기록은 "방금 던진 결과" 가 있어야 받아준다.
     * 실제 흐름과 똑같이 draw() 를 한 번 호출해 서버에 추첨 티켓을 만들어 둔다.
     */
    private void givenDrawn() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundMapper.excludedCoordinates(1L)).willReturn(List.of());
        given(regionCatalog.draw(any())).willReturn(region());

        throwRoundService.draw(DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID);
    }

    @Test
    @DisplayName("생성 - 첫 회차는 roundNo 1 로 저장된다")
    void create_firstRoundNo() {
        givenDrawn();
        given(regionCatalog.find(REGION_CODE)).willReturn(region());
        given(throwRoundRepository.findTopByThrowSessionIdOrderByRoundNoDesc(1L)).willReturn(Optional.empty());
        given(throwRoundRepository.save(any(ThrowRound.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwRoundService.create(createReqDto(), null, DEVICE_ID);

        ArgumentCaptor<ThrowRound> captor = ArgumentCaptor.forClass(ThrowRound.class);
        verify(throwRoundRepository).save(captor.capture());
        assertThat(captor.getValue().getRoundNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("생성 - roundNo 는 클라이언트 값이 아니라 직전 회차 + 1 로 채워진다")
    void create_nextRoundNo() {
        ThrowRound last = ThrowRound.of(1L, 4, "26110", 35.1, 129.0, ThrowChoice.AGAIN);

        givenDrawn();
        given(regionCatalog.find(REGION_CODE)).willReturn(region());
        given(throwRoundRepository.findTopByThrowSessionIdOrderByRoundNoDesc(1L)).willReturn(Optional.of(last));
        given(throwRoundRepository.save(any(ThrowRound.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwRoundService.create(createReqDto(), null, DEVICE_ID);

        ArgumentCaptor<ThrowRound> captor = ArgumentCaptor.forClass(ThrowRound.class);
        verify(throwRoundRepository).save(captor.capture());
        assertThat(captor.getValue().getRoundNo()).isEqualTo(5);
    }

    /* ── 추첨 우회 차단 ───────────────────────────────────── */

    @Test
    @DisplayName("생성 - 던지지 않고 회차부터 기록하려 하면 거부된다")
    void create_withoutDraw() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));

        assertThatThrownBy(() -> throwRoundService.create(createReqDto(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwRoundRepository, never()).save(any(ThrowRound.class));
    }

    @Test
    @DisplayName("생성 - 서버가 뽑은 지역이 아닌 코드를 보내면 거부된다")
    void create_regionCodeMismatch() {
        givenDrawn();

        // 서버는 속초를 뽑았는데 강남으로 바꿔치기하려는 요청
        assertThatThrownBy(() -> throwRoundService.create(createReqDto("11680"), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwRoundRepository, never()).save(any(ThrowRound.class));
    }

    @Test
    @DisplayName("생성 - 추첨 티켓은 1회용이라 같은 결과로 두 번 기록할 수 없다")
    void create_ticketIsSingleUse() {
        givenDrawn();
        given(regionCatalog.find(REGION_CODE)).willReturn(region());
        given(throwRoundRepository.findTopByThrowSessionIdOrderByRoundNoDesc(1L)).willReturn(Optional.empty());
        given(throwRoundRepository.save(any(ThrowRound.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwRoundService.create(createReqDto(), null, DEVICE_ID);

        assertThatThrownBy(() -> throwRoundService.create(createReqDto(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwRoundRepository).save(any(ThrowRound.class));   // 저장은 처음 한 번뿐
    }

    @Test
    @DisplayName("생성 - 좌표는 클라이언트 값이 아니라 카탈로그 값으로 기록된다")
    void create_overwritesClientCoordinates() {
        givenDrawn();
        given(regionCatalog.find(REGION_CODE)).willReturn(region());
        given(throwRoundRepository.findTopByThrowSessionIdOrderByRoundNoDesc(1L)).willReturn(Optional.empty());
        given(throwRoundRepository.save(any(ThrowRound.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwRoundService.create(createReqDto(), null, DEVICE_ID);

        ArgumentCaptor<ThrowRound> captor = ArgumentCaptor.forClass(ThrowRound.class);
        verify(throwRoundRepository).save(captor.capture());
        assertThat(captor.getValue().getLat()).isEqualTo(38.2070);
        assertThat(captor.getValue().getLng()).isEqualTo(128.5918);
    }

    @Test
    @DisplayName("생성 - 이미 확정된 세션에는 회차를 추가할 수 없다")
    void create_confirmedSession() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.CONFIRMED));

        assertThatThrownBy(() -> throwRoundService.create(createReqDto(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwRoundRepository, never()).save(any(ThrowRound.class));
    }

    @Test
    @DisplayName("생성 - 남의 세션에 회차를 남기려 하면 AccessDeniedException")
    void create_otherSession() {
        willThrow(new AccessDeniedException("본인의 세션만 접근할 수 있습니다."))
                .given(throwSessionService).detail(any(), any(), any());

        assertThatThrownBy(() -> throwRoundService.create(createReqDto(), null, "device-uuid-2"))
                .isInstanceOf(AccessDeniedException.class);

        verify(throwRoundRepository, never()).save(any(ThrowRound.class));
    }

    @Test
    @DisplayName("생성 - 세션 ID 가 없으면 InvalidRequestException")
    void create_withoutSessionId() {
        assertThatThrownBy(() -> throwRoundService.create(ThrowRoundDto.CreateReqDto.builder()
                .choice(ThrowChoice.AGAIN)
                .build(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwRoundRepository, never()).save(any(ThrowRound.class));
    }

    @Test
    @DisplayName("상세 조회 - 대상이 없으면 NoMatchingDataException")
    void detail_notFound() {
        given(throwRoundMapper.detail(1L)).willReturn(null);

        assertThatThrownBy(() -> throwRoundService.detail(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID))
                .isInstanceOf(NoMatchingDataException.class);
    }

    @Test
    @DisplayName("상세 조회 - 상위 세션이 남의 것이면 AccessDeniedException")
    void detail_otherSession() {
        given(throwRoundMapper.detail(1L)).willReturn(ThrowRoundDto.DetailResDto.builder()
                .id(1L)
                .throwSessionId(1L)
                .build());
        willThrow(new AccessDeniedException("본인의 세션만 접근할 수 있습니다."))
                .given(throwSessionService).detail(any(), any(), any());

        assertThatThrownBy(() -> throwRoundService.detail(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, "device-uuid-2"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("목록 조회 - Mapper 가 반환한 id 로 상세를 채운다")
    void list_fillsDetailById() {
        ThrowRoundDto.ListReqDto param = ThrowRoundDto.ListReqDto.builder().throwSessionId(1L).build();

        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundMapper.list(param)).willReturn(List.of(
                ThrowRoundDto.DetailResDto.builder().id(10L).build()));
        given(throwRoundMapper.detail(10L)).willReturn(ThrowRoundDto.DetailResDto.builder()
                .id(10L)
                .throwSessionId(1L)
                .roundNo(1)
                .choice(ThrowChoice.AGAIN)
                .build());

        List<ThrowRoundDto.DetailResDto> result = throwRoundService.list(param, null, DEVICE_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoundNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("블랙리스트 - 같은 세션에서 이미 뽑힌 좌표를 반환한다")
    void excludedCoordinates_returnsPreviousCoordinates() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundMapper.excludedCoordinates(1L)).willReturn(List.of(
                ThrowRoundDto.CoordinateResDto.builder().regionCode("11").lat(37.5).lng(127.0).build(),
                ThrowRoundDto.CoordinateResDto.builder().regionCode("26").lat(35.1).lng(129.0).build()
        ));

        List<ThrowRoundDto.CoordinateResDto> result = throwRoundService.excludedCoordinates(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ThrowRoundDto.CoordinateResDto::getRegionCode)
                .containsExactly("11", "26");
    }

    @Test
    @DisplayName("블랙리스트 - 남의 세션 좌표는 조회할 수 없다")
    void excludedCoordinates_otherSession() {
        willThrow(new AccessDeniedException("본인의 세션만 접근할 수 있습니다."))
                .given(throwSessionService).detail(any(), any(), any());

        assertThatThrownBy(() -> throwRoundService.excludedCoordinates(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, "device-uuid-2"))
                .isInstanceOf(AccessDeniedException.class);

        verify(throwRoundMapper, never()).excludedCoordinates(any());
    }

    @Test
    @DisplayName("블랙리스트 - 세션 ID 가 없으면 InvalidRequestException")
    void excludedCoordinates_withoutSessionId() {
        assertThatThrownBy(() -> throwRoundService.excludedCoordinates(
                DefaultDto.DetailReqDto.builder().build(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwRoundMapper, never()).excludedCoordinates(any());
    }

    /* ── 추첨 ────────────────────────────────────────────── */

    @Test
    @DisplayName("추첨 - 카탈로그가 고른 지역을 그대로 응답으로 옮긴다")
    void draw_returnsCatalogRegion() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundMapper.excludedCoordinates(1L)).willReturn(List.of());
        given(regionCatalog.draw(any())).willReturn(region());

        ThrowRoundDto.DrawResDto result = throwRoundService.draw(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID);

        assertThat(result.getRegionCode()).isEqualTo("42210");
        assertThat(result.getRegionName()).isEqualTo("강원특별자치도 속초시");
        assertThat(result.getLat()).isEqualTo(38.2070);
        assertThat(result.getLng()).isEqualTo(128.5918);
    }

    @Test
    @DisplayName("추첨 - 말래(재던지기) 시 이미 뽑힌 지역 코드가 제외 목록으로 넘어간다")
    void draw_passesExcludedCodes() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundMapper.excludedCoordinates(1L)).willReturn(List.of(
                ThrowRoundDto.CoordinateResDto.builder().regionCode("11110").build(),
                ThrowRoundDto.CoordinateResDto.builder().regionCode("26110").build()
        ));
        given(regionCatalog.draw(any())).willReturn(region());

        throwRoundService.draw(DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(regionCatalog).draw(captor.capture());
        assertThat(captor.getValue()).containsExactly("11110", "26110");
    }

    @Test
    @DisplayName("추첨 - regionCode 가 null 인 회차는 제외 목록에서 걸러진다")
    void draw_skipsNullRegionCode() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundMapper.excludedCoordinates(1L)).willReturn(List.of(
                ThrowRoundDto.CoordinateResDto.builder().regionCode("11110").build(),
                ThrowRoundDto.CoordinateResDto.builder().regionCode(null).build()
        ));
        given(regionCatalog.draw(any())).willReturn(region());

        throwRoundService.draw(DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(regionCatalog).draw(captor.capture());
        assertThat(captor.getValue()).containsExactly("11110");
    }

    @Test
    @DisplayName("추첨 - 이미 확정된 세션에서는 다시 추첨할 수 없다")
    void draw_confirmedSession() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.CONFIRMED));

        assertThatThrownBy(() -> throwRoundService.draw(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(regionCatalog, never()).draw(any());
    }

    @Test
    @DisplayName("추첨 - 남의 세션은 추첨할 수 없다")
    void draw_otherSession() {
        willThrow(new AccessDeniedException("본인의 세션만 접근할 수 있습니다."))
                .given(throwSessionService).detail(any(), any(), any());

        assertThatThrownBy(() -> throwRoundService.draw(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, "device-uuid-2"))
                .isInstanceOf(AccessDeniedException.class);

        verify(regionCatalog, never()).draw(any());
    }

    @Test
    @DisplayName("추첨 - 세션 ID 가 없으면 InvalidRequestException")
    void draw_withoutSessionId() {
        assertThatThrownBy(() -> throwRoundService.draw(
                DefaultDto.DetailReqDto.builder().build(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(regionCatalog, never()).draw(any());
    }
}
