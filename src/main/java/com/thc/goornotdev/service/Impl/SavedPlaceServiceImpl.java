package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.SavedPlaceDto;
import com.thc.goornotdev.domain.SavedPlace;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.SavedPlaceMapper;
import com.thc.goornotdev.repository.SavedPlaceRepository;
import com.thc.goornotdev.service.CardService;
import com.thc.goornotdev.service.SavedPlaceService;
import com.thc.goornotdev.service.ThrowSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class SavedPlaceServiceImpl implements SavedPlaceService {
    private final SavedPlaceRepository savedPlaceRepository;
    private final SavedPlaceMapper savedPlaceMapper;

    // 저장 요청이 내 세션에서 나온 것인지 확인하기 위해 세션 서비스를 주입한다
    private final ThrowSessionService throwSessionService;

    /*
     * "여기 간다" 가 켜지면 그 카테고리의 카드 판을 끝내기 위해 카드 서비스를 주입한다.
     *
     * 순환 참조가 아니다 — CardService 는 PlaceService 와 ThrowSessionService 만 알고
     * SavedPlaceService 를 알지 못한다. 화살표는 한 방향으로만 간다.
     */
    private final CardService cardService;

    @Override
    @Transactional
    public DefaultDto.CreateResDto create(SavedPlaceDto.CreateReqDto param, Long reqUserId, String reqDeviceId) {
        verifyLogin(reqUserId);

        // 남의 세션 ID 를 붙여 저장하지 못하도록 상위 세션 소유권을 먼저 확인한다
        throwSessionService.detail(DefaultDto.DetailReqDto.builder()
                .id(param.getThrowSessionId())
                .build(), reqUserId, reqDeviceId);

        if (param.getVisited() == null && param.getWished() == null) {
            throw new InvalidRequestException("켜거나 끌 표시를 지정해야 합니다.");
        }

        /*
         * 한 장소에 표시가 두 개라 저장은 INSERT 가 아니라 upsert 다.
         *
         * (여행, 장소) 당 행은 하나뿐이고 visited / wished 가 그 위에서 따로 켜진다.
         * 하트를 누른 곳에 나중에 "여기 간다" 를 켜는 일이 흔하므로, 이미 행이 있으면
         * 중복으로 막지 않고 요청이 지정한 플래그만 갱신한다.
         *
         * Soft Delete 된 행도 UNIQUE 자리를 차지하고 있어서 같은 경로로 되살린다.
         * (그래서 deleted 조건 없이 조회한다)
         */
        SavedPlace place = savedPlaceRepository
                .findByUserIdAndThrowSessionIdAndContentId(reqUserId, param.getThrowSessionId(), param.getContentId())
                .orElse(null);

        if (place == null) {
            place = param.toEntity(reqUserId);
        } else {
            place.mark(param);
        }

        // 표시가 하나도 남지 않으면 행을 남겨둘 이유가 없다 (해제 = Soft Delete)
        if (place.isBlank()) {
            place.delete();
        }

        DefaultDto.CreateResDto result = savedPlaceRepository.save(place).toCreateResDto();

        /*
         * "여기 간다" 를 켰으면 그 카테고리의 판은 끝이다. 깔려 있던 카드와 남은 리롤을 버린다.
         * 다음에 같은 카테고리를 열면 새 카드가 깔린다 (기획안 5번 [2026-09-19]).
         *
         * 하트(wished)만 눌렀을 때는 버리지 않는다. 찜은 "나중에 볼까" 라서 고르는 행위가 아니고,
         * 여기서 판을 끝내면 세 장 중 둘을 찜해두는 것만으로 카드가 사라진다.
         *
         * 커밋 전이라 저장이 롤백되면 풀만 억울하게 날아가지만, 그 대가는 "카드를 다시 받는다"
         * 뿐이라 트랜잭션 동기화까지 걸 만한 무게가 아니다.
         */
        if (Boolean.TRUE.equals(param.getVisited())) {
            cardService.endRound(param.getThrowSessionId(), param.getCategory());
        }

        return result;
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
