package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.domain.SavedPlace;
import com.thc.goornotdev.domain.ThrowChoice;
import com.thc.goornotdev.domain.ThrowRound;
import com.thc.goornotdev.domain.ThrowSession;
import com.thc.goornotdev.domain.ThrowSource;
import com.thc.goornotdev.domain.ThrowStatus;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.ThrowSessionMapper;
import com.thc.goornotdev.repository.SavedPlaceRepository;
import com.thc.goornotdev.repository.ThrowRoundRepository;
import com.thc.goornotdev.repository.ThrowSessionRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThrowSessionServiceImplTest {

    private static final String DEVICE_ID = "device-uuid-1";

    @Mock
    private ThrowSessionRepository throwSessionRepository;

    @Mock
    private ThrowSessionMapper throwSessionMapper;

    @Mock
    private ThrowRoundRepository throwRoundRepository;

    @Mock
    private SavedPlaceRepository savedPlaceRepository;

    @InjectMocks
    private ThrowSessionServiceImpl throwSessionService;

    private ThrowSession anonymousSession() {
        ThrowSession session = ThrowSession.of(null, DEVICE_ID, ThrowSource.RANDOM, null,
                ThrowStatus.IN_PROGRESS, null, null, null, null);
        session.setId(1L);
        session.setDeleted(false);
        return session;
    }

    @Test
    @DisplayName("생성 - RANDOM 은 IN_PROGRESS 상태로 저장된다")
    void create_randomStartsInProgress() {
        given(throwSessionRepository.save(any(ThrowSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwSessionService.create(ThrowSessionDto.CreateReqDto.builder()
                .source(ThrowSource.RANDOM)
                .build(), null, DEVICE_ID);

        ArgumentCaptor<ThrowSession> captor = ArgumentCaptor.forClass(ThrowSession.class);
        verify(throwSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ThrowStatus.IN_PROGRESS);
        assertThat(captor.getValue().getDeviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    @DisplayName("생성 - SEARCH 는 좌표가 있으면 CONFIRMED 로 저장된다")
    void create_searchStartsConfirmed() {
        given(throwSessionRepository.save(any(ThrowSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        throwSessionService.create(ThrowSessionDto.CreateReqDto.builder()
                .source(ThrowSource.SEARCH)
                .regionCode("11")
                .regionName("서울")
                .lat(37.5)
                .lng(127.0)
                .build(), 1L, DEVICE_ID);

        ArgumentCaptor<ThrowSession> captor = ArgumentCaptor.forClass(ThrowSession.class);
        verify(throwSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ThrowStatus.CONFIRMED);
    }

    @Test
    @DisplayName("생성 - SEARCH 인데 좌표가 없으면 InvalidRequestException")
    void create_searchWithoutCoordinates() {
        assertThatThrownBy(() -> throwSessionService.create(ThrowSessionDto.CreateReqDto.builder()
                .source(ThrowSource.SEARCH)
                .build(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwSessionRepository, never()).save(any(ThrowSession.class));
    }

    @Test
    @DisplayName("생성 - X-Device-Id 가 없으면 InvalidRequestException")
    void create_withoutDeviceId() {
        assertThatThrownBy(() -> throwSessionService.create(ThrowSessionDto.CreateReqDto.builder()
                .source(ThrowSource.RANDOM)
                .build(), null, null))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwSessionRepository, never()).save(any(ThrowSession.class));
    }

    @Test
    @DisplayName("수정 - 확정하려는데 지역 정보가 없으면 InvalidRequestException")
    void update_confirmWithoutRegion() {
        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(anonymousSession()));

        assertThatThrownBy(() -> throwSessionService.update(ThrowSessionDto.UpdateReqDto.builder()
                .id(1L)
                .status(ThrowStatus.CONFIRMED)
                .build(), null, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class);

        verify(throwSessionRepository, never()).save(any(ThrowSession.class));
    }

    @Test
    @DisplayName("수정 - 지역 정보를 함께 주면 확정된다")
    void update_confirmWithRegion() {
        ThrowSession session = anonymousSession();
        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(session));

        throwSessionService.update(ThrowSessionDto.UpdateReqDto.builder()
                .id(1L)
                .status(ThrowStatus.CONFIRMED)
                .totalCount(3)
                .regionCode("11")
                .regionName("서울")
                .lat(37.5)
                .lng(127.0)
                .build(), null, DEVICE_ID);

        assertThat(session.getStatus()).isEqualTo(ThrowStatus.CONFIRMED);
        assertThat(session.getTotalCount()).isEqualTo(3);
        verify(throwSessionRepository).save(session);
    }

    @Test
    @DisplayName("수정 - 다른 기기의 익명 세션은 수정할 수 없다")
    void update_otherDevice() {
        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(anonymousSession()));

        assertThatThrownBy(() -> throwSessionService.update(ThrowSessionDto.UpdateReqDto.builder()
                .id(1L)
                .totalCount(3)
                .build(), null, "device-uuid-2"))
                .isInstanceOf(AccessDeniedException.class);

        verify(throwSessionRepository, never()).save(any(ThrowSession.class));
    }

    @Test
    @DisplayName("수정 - 주인이 있는 세션은 deviceId 가 같아도 타인이 수정할 수 없다")
    void update_ownedSessionRejectsDeviceId() {
        ThrowSession session = anonymousSession();
        session.setUserId(1L);

        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> throwSessionService.update(ThrowSessionDto.UpdateReqDto.builder()
                .id(1L)
                .totalCount(3)
                .build(), null, DEVICE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("연결 - 익명 세션에 로그인한 사용자를 연결한다")
    void linkUser_setsUserId() {
        ThrowSession session = anonymousSession();
        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(session));

        throwSessionService.linkUser(DefaultDto.DetailReqDto.builder().id(1L).build(), 7L, DEVICE_ID);

        assertThat(session.getUserId()).isEqualTo(7L);
        verify(throwSessionRepository).save(session);
    }

    @Test
    @DisplayName("연결 - 비로그인 상태로는 연결할 수 없다")
    void linkUser_requiresLogin() {
        assertThatThrownBy(() -> throwSessionService.linkUser(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(throwSessionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("삭제 - 하위 회차와 저장 장소까지 함께 Soft Delete 된다")
    void delete_cascadesToChildren() {
        ThrowSession session = anonymousSession();
        ThrowRound round = ThrowRound.of(1L, 1, "11", 37.5, 127.0, ThrowChoice.AGAIN);
        SavedPlace place = SavedPlace.of(7L, "content-1", 1L, "관광지", "경복궁", "서울", 37.5, 127.0);

        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(throwRoundRepository.findByThrowSessionIdAndDeletedFalse(1L)).willReturn(List.of(round));
        given(savedPlaceRepository.findByThrowSessionIdAndDeletedFalse(1L)).willReturn(List.of(place));

        throwSessionService.delete(ThrowSessionDto.UpdateReqDto.builder().id(1L).build(), null, DEVICE_ID);

        assertThat(round.getDeleted()).isTrue();
        assertThat(place.getDeleted()).isTrue();
        assertThat(session.getDeleted()).isTrue();
        verify(throwRoundRepository).saveAll(List.of(round));
        verify(savedPlaceRepository).saveAll(List.of(place));
        verify(throwSessionRepository).save(session);
    }

    @Test
    @DisplayName("삭제 - 이미 삭제된 세션은 NoMatchingDataException")
    void delete_alreadyDeleted() {
        ThrowSession session = anonymousSession();
        session.setDeleted(true);

        given(throwSessionRepository.findById(1L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> throwSessionService.delete(
                ThrowSessionDto.UpdateReqDto.builder().id(1L).build(), null, DEVICE_ID))
                .isInstanceOf(NoMatchingDataException.class);
    }

    @Test
    @DisplayName("상세 조회 - 대상이 없으면 NoMatchingDataException")
    void detail_notFound() {
        given(throwSessionMapper.detail(1L)).willReturn(null);

        assertThatThrownBy(() -> throwSessionService.detail(
                DefaultDto.DetailReqDto.builder().id(1L).build(), null, DEVICE_ID))
                .isInstanceOf(NoMatchingDataException.class);
    }

    @Test
    @DisplayName("상세 조회 - 타인의 세션이면 AccessDeniedException")
    void detail_otherUser() {
        given(throwSessionMapper.detail(1L)).willReturn(ThrowSessionDto.DetailResDto.builder()
                .id(1L)
                .userId(2L)
                .build());

        assertThatThrownBy(() -> throwSessionService.detail(
                DefaultDto.DetailReqDto.builder().id(1L).build(), 1L, DEVICE_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("목록 조회 - 클라이언트가 보낸 소유자 조건을 요청자 정보로 덮어쓴다")
    void list_overwritesOwnerCondition() {
        ThrowSessionDto.ListReqDto param = ThrowSessionDto.ListReqDto.builder()
                .userId(999L)              // 남의 것을 보려는 시도
                .deviceId("other-device")
                .build();

        given(throwSessionMapper.list(param)).willReturn(List.of(
                ThrowSessionDto.DetailResDto.builder().id(1L).build()));
        given(throwSessionMapper.detail(1L)).willReturn(ThrowSessionDto.DetailResDto.builder()
                .id(1L)
                .userId(7L)
                .build());

        List<ThrowSessionDto.DetailResDto> result = throwSessionService.list(param, 7L, DEVICE_ID);

        assertThat(param.getUserId()).isEqualTo(7L);
        assertThat(param.getDeviceId()).isNull();   // 로그인 상태면 deviceId 조건을 쓰지 않는다
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("목록 조회 - 비로그인은 요청자 deviceId 로 조건이 고정된다")
    void list_anonymousUsesDeviceId() {
        ThrowSessionDto.ListReqDto param = ThrowSessionDto.ListReqDto.builder()
                .userId(999L)
                .deviceId("other-device")
                .build();

        given(throwSessionMapper.list(param)).willReturn(List.of());

        throwSessionService.list(param, null, DEVICE_ID);

        assertThat(param.getUserId()).isNull();
        assertThat(param.getDeviceId()).isEqualTo(DEVICE_ID);
    }
}
