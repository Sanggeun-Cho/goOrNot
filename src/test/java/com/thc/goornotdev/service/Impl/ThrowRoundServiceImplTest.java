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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

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

    @InjectMocks
    private ThrowRoundServiceImpl throwRoundService;

    private ThrowSessionDto.DetailResDto session(ThrowStatus status) {
        return ThrowSessionDto.DetailResDto.builder()
                .id(1L)
                .deviceId(DEVICE_ID)
                .status(status)
                .build();
    }

    private ThrowRoundDto.CreateReqDto createReqDto() {
        return ThrowRoundDto.CreateReqDto.builder()
                .throwSessionId(1L)
                .regionCode("11")
                .lat(37.5)
                .lng(127.0)
                .choice(ThrowChoice.AGAIN)
                .build();
    }

    @Test
    @DisplayName("생성 - 첫 회차는 roundNo 1 로 저장된다")
    void create_firstRoundNo() {
        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
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
        ThrowRound last = ThrowRound.of(1L, 4, "26", 35.1, 129.0, ThrowChoice.AGAIN);

        given(throwSessionService.detail(any(), any(), any())).willReturn(session(ThrowStatus.IN_PROGRESS));
        given(throwRoundRepository.findTopByThrowSessionIdOrderByRoundNoDesc(1L)).willReturn(Optional.of(last));
        given(throwRoundRepository.save(any(ThrowRound.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwRoundService.create(createReqDto(), null, DEVICE_ID);

        ArgumentCaptor<ThrowRound> captor = ArgumentCaptor.forClass(ThrowRound.class);
        verify(throwRoundRepository).save(captor.capture());
        assertThat(captor.getValue().getRoundNo()).isEqualTo(5);
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
}
