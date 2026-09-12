package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.SavedPlaceDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.domain.SavedPlace;
import com.thc.goornotdev.exception.DuplicateDataException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.SavedPlaceMapper;
import com.thc.goornotdev.repository.SavedPlaceRepository;
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
class SavedPlaceServiceImplTest {

    private static final String DEVICE_ID = "device-uuid-1";

    @Mock
    private SavedPlaceRepository savedPlaceRepository;

    @Mock
    private SavedPlaceMapper savedPlaceMapper;

    @Mock
    private ThrowSessionService throwSessionService;

    @InjectMocks
    private SavedPlaceServiceImpl savedPlaceService;

    private SavedPlaceDto.CreateReqDto createReqDto() {
        return SavedPlaceDto.CreateReqDto.builder()
                .throwSessionId(1L)
                .contentId("content-1")
                .category("관광지")
                .placeName("경복궁")
                .address("서울 종로구")
                .lat(37.5)
                .lng(127.0)
                .build();
    }

    private SavedPlace savedPlace() {
        SavedPlace place = SavedPlace.of(7L, "content-1", 1L, "관광지", "경복궁", "서울 종로구", 37.5, 127.0);
        place.setId(100L);
        place.setDeleted(false);
        return place;
    }

    @Test
    @DisplayName("저장 - 처음 저장하는 장소는 새로 INSERT 된다")
    void create_newPlace() {
        given(throwSessionService.detail(any(), any(), any()))
                .willReturn(ThrowSessionDto.DetailResDto.builder().id(1L).userId(7L).build());
        given(savedPlaceRepository.findByUserIdAndContentId(7L, "content-1")).willReturn(Optional.empty());
        given(savedPlaceRepository.save(any(SavedPlace.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        savedPlaceService.create(createReqDto(), 7L, DEVICE_ID);

        ArgumentCaptor<SavedPlace> captor = ArgumentCaptor.forClass(SavedPlace.class);
        verify(savedPlaceRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getContentId()).isEqualTo("content-1");
    }

    @Test
    @DisplayName("저장 - 이미 저장한 장소를 또 저장하면 DuplicateDataException")
    void create_duplicateContentId() {
        given(throwSessionService.detail(any(), any(), any()))
                .willReturn(ThrowSessionDto.DetailResDto.builder().id(1L).userId(7L).build());
        given(savedPlaceRepository.findByUserIdAndContentId(7L, "content-1"))
                .willReturn(Optional.of(savedPlace()));

        assertThatThrownBy(() -> savedPlaceService.create(createReqDto(), 7L, DEVICE_ID))
                .isInstanceOf(DuplicateDataException.class)
                .hasMessageContaining("content-1");

        verify(savedPlaceRepository, never()).save(any(SavedPlace.class));
    }

    @Test
    @DisplayName("저장 - 해제했던 장소를 다시 저장하면 기존 행을 되살린다 (UNIQUE 충돌 방지)")
    void create_restoresSoftDeletedRow() {
        SavedPlace place = savedPlace();
        place.setDeleted(true);

        given(throwSessionService.detail(any(), any(), any()))
                .willReturn(ThrowSessionDto.DetailResDto.builder().id(2L).userId(7L).build());
        given(savedPlaceRepository.findByUserIdAndContentId(7L, "content-1")).willReturn(Optional.of(place));
        given(savedPlaceRepository.save(any(SavedPlace.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SavedPlaceDto.CreateReqDto param = createReqDto();
        param.setThrowSessionId(2L);     // 다른 세션에서 다시 하트

        DefaultDto.CreateResDto result = savedPlaceService.create(param, 7L, DEVICE_ID);

        assertThat(place.getDeleted()).isFalse();
        assertThat(place.getThrowSessionId()).isEqualTo(2L);
        assertThat(result.getId()).isEqualTo(100L);   // 새 행이 아니라 기존 행
        verify(savedPlaceRepository).save(place);
    }

    @Test
    @DisplayName("저장 - 비로그인 상태로는 저장할 수 없다")
    void create_requiresLogin() {
        assertThatThrownBy(() -> savedPlaceService.create(createReqDto(), null, DEVICE_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(savedPlaceRepository, never()).save(any(SavedPlace.class));
    }

    @Test
    @DisplayName("저장 - 남의 세션 ID 를 붙이면 AccessDeniedException")
    void create_otherSession() {
        willThrow(new AccessDeniedException("본인의 세션만 접근할 수 있습니다."))
                .given(throwSessionService).detail(any(), any(), any());

        assertThatThrownBy(() -> savedPlaceService.create(createReqDto(), 7L, DEVICE_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(savedPlaceRepository, never()).save(any(SavedPlace.class));
    }

    @Test
    @DisplayName("해제 - Soft Delete 처리된다")
    void delete_softDeletes() {
        SavedPlace place = savedPlace();
        given(savedPlaceRepository.findById(100L)).willReturn(Optional.of(place));

        savedPlaceService.delete(DefaultDto.DetailReqDto.builder().id(100L).build(), 7L);

        assertThat(place.getDeleted()).isTrue();
        verify(savedPlaceRepository).save(place);
    }

    @Test
    @DisplayName("해제 - 타인이 저장한 장소는 해제할 수 없다")
    void delete_otherUser() {
        given(savedPlaceRepository.findById(100L)).willReturn(Optional.of(savedPlace()));

        assertThatThrownBy(() -> savedPlaceService.delete(
                DefaultDto.DetailReqDto.builder().id(100L).build(), 8L))
                .isInstanceOf(AccessDeniedException.class);

        verify(savedPlaceRepository, never()).save(any(SavedPlace.class));
    }

    @Test
    @DisplayName("해제 - 이미 해제된 장소는 NoMatchingDataException")
    void delete_alreadyDeleted() {
        SavedPlace place = savedPlace();
        place.setDeleted(true);

        given(savedPlaceRepository.findById(100L)).willReturn(Optional.of(place));

        assertThatThrownBy(() -> savedPlaceService.delete(
                DefaultDto.DetailReqDto.builder().id(100L).build(), 7L))
                .isInstanceOf(NoMatchingDataException.class);
    }

    @Test
    @DisplayName("상세 조회 - 대상이 없으면 NoMatchingDataException")
    void detail_notFound() {
        given(savedPlaceMapper.detail(100L)).willReturn(null);

        assertThatThrownBy(() -> savedPlaceService.detail(
                DefaultDto.DetailReqDto.builder().id(100L).build(), 7L))
                .isInstanceOf(NoMatchingDataException.class);
    }

    @Test
    @DisplayName("상세 조회 - 타인이 저장한 장소면 AccessDeniedException")
    void detail_otherUser() {
        given(savedPlaceMapper.detail(100L)).willReturn(SavedPlaceDto.DetailResDto.builder()
                .id(100L)
                .userId(8L)
                .build());

        assertThatThrownBy(() -> savedPlaceService.detail(
                DefaultDto.DetailReqDto.builder().id(100L).build(), 7L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("목록 조회 - 클라이언트가 보낸 userId 를 요청자 ID 로 덮어쓴다")
    void list_overwritesUserId() {
        SavedPlaceDto.ListReqDto param = SavedPlaceDto.ListReqDto.builder()
                .userId(999L)   // 남의 것을 보려는 시도
                .build();

        given(savedPlaceMapper.list(param)).willReturn(List.of(
                SavedPlaceDto.DetailResDto.builder().id(100L).build()));
        given(savedPlaceMapper.detail(100L)).willReturn(SavedPlaceDto.DetailResDto.builder()
                .id(100L)
                .userId(7L)
                .placeName("경복궁")
                .build());

        List<SavedPlaceDto.DetailResDto> result = savedPlaceService.list(param, 7L);

        assertThat(param.getUserId()).isEqualTo(7L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlaceName()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("목록 조회 - 비로그인 상태로는 조회할 수 없다")
    void list_requiresLogin() {
        assertThatThrownBy(() -> savedPlaceService.list(SavedPlaceDto.ListReqDto.builder().build(), null))
                .isInstanceOf(AccessDeniedException.class);

        verify(savedPlaceMapper, never()).list(any());
    }
}
