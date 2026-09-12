package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.SavedPlaceDto;
import com.thc.goornotdev.domain.SavedPlace;
import com.thc.goornotdev.exception.DuplicateDataException;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.SavedPlaceMapper;
import com.thc.goornotdev.repository.SavedPlaceRepository;
import com.thc.goornotdev.service.SavedPlaceService;
import com.thc.goornotdev.service.ThrowSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class SavedPlaceServiceImpl implements SavedPlaceService {
    private final SavedPlaceRepository savedPlaceRepository;
    private final SavedPlaceMapper savedPlaceMapper;

    // 저장 요청이 내 세션에서 나온 것인지 확인하기 위해 세션 서비스를 주입한다
    private final ThrowSessionService throwSessionService;

    @Override
    @Transactional
    public DefaultDto.CreateResDto create(SavedPlaceDto.CreateReqDto param, Long reqUserId, String reqDeviceId) {
        verifyLogin(reqUserId);

        // 남의 세션 ID 를 붙여 저장하지 못하도록 상위 세션 소유권을 먼저 확인한다
        throwSessionService.detail(DefaultDto.DetailReqDto.builder()
                .id(param.getThrowSessionId())
                .build(), reqUserId, reqDeviceId);

        /*
         * Soft Delete 와 UNIQUE(user_id, content_id) 가 충돌하는 지점이다.
         * 하트를 해제해도 행이 남아 있어서 그대로 INSERT 하면 제약 위반이 난다.
         * 그래서 삭제 여부와 무관하게 행을 먼저 찾고,
         *   - 살아 있으면 중복 저장으로 보고 막는다
         *   - 해제된 상태면 새 세션 정보로 되살린다 (하트 다시 누르기)
         */
        Optional<SavedPlace> existing = savedPlaceRepository.findByUserIdAndContentId(reqUserId, param.getContentId());

        if (existing.isPresent()) {
            SavedPlace place = existing.get();

            if (!Boolean.TRUE.equals(place.getDeleted())) {
                throw new DuplicateDataException("contentId : " + param.getContentId());
            }

            place.restore(param);

            return savedPlaceRepository.save(place).toCreateResDto();
        }

        return savedPlaceRepository.save(param.toEntity(reqUserId)).toCreateResDto();
    }

    @Override
    @Transactional
    public void delete(DefaultDto.DetailReqDto param, Long reqUserId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("해제할 장소 ID 가 필요합니다.");
        }
        verifyLogin(reqUserId);

        SavedPlace place = savedPlaceRepository.findById(param.getId())
                .orElseThrow(() -> new NoMatchingDataException("id : " + param.getId()));

        if (Boolean.TRUE.equals(place.getDeleted())) {
            throw new NoMatchingDataException("id : " + param.getId());
        }

        verifyOwner(place.getUserId(), reqUserId);

        place.delete();
        savedPlaceRepository.save(place);
    }

    // Mapper 를 이용한 장소 조회 함수. 소유권 검증을 여기서 한 번에 처리한다
    public SavedPlaceDto.DetailResDto get(DefaultDto.DetailReqDto param, Long reqUserId) {
        SavedPlaceDto.DetailResDto place = savedPlaceMapper.detail(param.getId());

        if (place == null) {
            throw new NoMatchingDataException("id : " + param.getId());
        }

        verifyOwner(place.getUserId(), reqUserId);

        return place;
    }

    @Override
    @Transactional(readOnly = true)
    public SavedPlaceDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("조회할 장소 ID 가 필요합니다.");
        }
        verifyLogin(reqUserId);

        return get(param, reqUserId);
    }

    // Mapper 를 통해 받은 장소 리스트의 ID 값을 이용해 상세 객체 리스트로 채움
    public List<SavedPlaceDto.DetailResDto> addList(List<SavedPlaceDto.DetailResDto> list, Long reqUserId) {
        List<SavedPlaceDto.DetailResDto> newList = new ArrayList<>();

        for (SavedPlaceDto.DetailResDto place : list) {
            newList.add(get(DefaultDto.DetailReqDto.builder()
                    .id(place.getId())
                    .build(), reqUserId));
        }

        return newList;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedPlaceDto.DetailResDto> list(SavedPlaceDto.ListReqDto param, Long reqUserId) {
        verifyLogin(reqUserId);

        // 클라이언트가 보낸 소유자 조건은 신뢰하지 않는다. 요청자 ID 로 덮어써 본인 것만 조회되게 한다
        param.setUserId(reqUserId);

        return addList(savedPlaceMapper.list(param), reqUserId);
    }

    /* ── 내부 공통 ───────────────────────────────────────── */

    private void verifyLogin(Long reqUserId) {
        if (reqUserId == null) {
            throw new AccessDeniedException("로그인 후에만 장소를 저장할 수 있습니다.");
        }
    }

    private void verifyOwner(Long ownerUserId, Long reqUserId) {
        if (ownerUserId == null || !ownerUserId.equals(reqUserId)) {
            throw new AccessDeniedException("본인이 저장한 장소만 접근할 수 있습니다.");
        }
    }
}
