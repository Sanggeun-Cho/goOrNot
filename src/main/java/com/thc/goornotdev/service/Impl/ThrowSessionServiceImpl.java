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
import com.thc.goornotdev.service.ThrowSessionService;
import com.thc.goornotdev.util.RegionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ThrowSessionServiceImpl implements ThrowSessionService {
    private final ThrowSessionRepository throwSessionRepository;
    private final ThrowSessionMapper throwSessionMapper;

    // 계층 삭제를 서비스에서 직접 순회하기 위해 하위 도메인 Repository 를 함께 주입한다.
    // 하위 서비스를 주입하면 ThrowRound/SavedPlace 가 세션 소유권 검증을 위해
    // 다시 ThrowSessionService 를 필요로 해 순환 참조가 된다.
    private final ThrowRoundRepository throwRoundRepository;
    private final SavedPlaceRepository savedPlaceRepository;

    // 클라이언트가 보낸 지역 정보를 서버 기준값으로 다시 해석하기 위한 카탈로그
    private final RegionCatalog regionCatalog;

    @Override
    @Transactional
    public DefaultDto.CreateResDto create(ThrowSessionDto.CreateReqDto param, Long reqUserId, String reqDeviceId) {
        verifyDeviceId(reqDeviceId);

        if (ThrowSource.SEARCH.equals(param.getSource())) {
            // SEARCH 는 사용자가 지역을 직접 고른 것이라 생성 시점에 지역이 확정돼 있어야 한다.
            // 좌표는 받되 쓰지 않는다 — 코드로 카탈로그에서 다시 찾아 덮어쓴다
            if (isBlank(param.getRegionCode())) {
                throw new InvalidRequestException("검색으로 시작한 세션은 지역 코드가 필요합니다.");
            }

            RegionCatalog.Region region = findRegion(param.getRegionCode());

            param.setRegionCode(region.code());
            param.setRegionName(region.name());
            param.setLat(region.lat());
            param.setLng(region.lng());
        } else {
            // RANDOM 은 던져서 확정하는 경로다. 생성 시점 지역 정보는 받지 않고 버린다.
            // 그대로 두면 던지기 전부터 지역이 박힌 세션이 만들어진다
            param.setRegionCode(null);
            param.setRegionName(null);
            param.setLat(null);
            param.setLng(null);
        }

        ThrowSession newSession = throwSessionRepository.save(param.toEntity(reqUserId, reqDeviceId));

        return newSession.toCreateResDto();
    }

    @Override
    @Transactional
    public void update(ThrowSessionDto.UpdateReqDto param, Long reqUserId, String reqDeviceId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("수정할 세션 ID 가 필요합니다.");
        }

        ThrowSession session = getEntity(param.getId(), reqUserId, reqDeviceId);

        // 요청에 담긴 지역 정보를 서버 기준으로 검증·정규화한 뒤에 반영한다
        normalizeRegion(param, session);

        session.update(param);

        // 확정 상태로 넘어가려면 갈 지역이 정해져 있어야 한다.
        // 요청값과 기존값을 합친 결과를 보고 판단한다
        if (ThrowStatus.CONFIRMED.equals(session.getStatus())
                && (isBlank(session.getRegionCode()) || session.getLat() == null || session.getLng() == null)) {
            throw new InvalidRequestException("세션을 확정하려면 지역 코드와 좌표가 필요합니다.");
        }

        throwSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void linkUser(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("연결할 세션 ID 가 필요합니다.");
        }
        if (reqUserId == null) {
            throw new AccessDeniedException("로그인 후에만 세션을 연결할 수 있습니다.");
        }

        ThrowSession session = getEntity(param.getId(), reqUserId, reqDeviceId);

        // 트리거한 세션 하나만 대상. 같은 deviceId 의 다른 세션은 익명으로 남겨둔다
        session.linkUser(reqUserId);

        throwSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void delete(ThrowSessionDto.UpdateReqDto param, Long reqUserId, String reqDeviceId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("삭제할 세션 ID 가 필요합니다.");
        }

        ThrowSession session = getEntity(param.getId(), reqUserId, reqDeviceId);

        // JPA Cascade 를 쓰지 않고 하위 엔티티를 직접 순회하며 각각 Soft Delete 한다
        List<ThrowRound> rounds = throwRoundRepository.findByThrowSessionIdAndDeletedFalse(session.getId());
        for (ThrowRound round : rounds) {
            round.delete();
        }
        throwRoundRepository.saveAll(rounds);

        List<SavedPlace> places = savedPlaceRepository.findByThrowSessionIdAndDeletedFalse(session.getId());
        for (SavedPlace place : places) {
            place.delete();
        }
        savedPlaceRepository.saveAll(places);

        session.delete();
        throwSessionRepository.save(session);
    }

    // Mapper 를 이용한 세션 조회 함수. 소유권 검증을 여기서 한 번에 처리한다
    public ThrowSessionDto.DetailResDto get(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId) {
        ThrowSessionDto.DetailResDto session = throwSessionMapper.detail(param.getId());

        if (session == null) {
            throw new NoMatchingDataException("id : " + param.getId());
        }

        verifyOwner(session.getUserId(), session.getDeviceId(), reqUserId, reqDeviceId);

        return session;
    }

    @Override
    @Transactional(readOnly = true)
    public ThrowSessionDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("조회할 세션 ID 가 필요합니다.");
        }

        return get(param, reqUserId, reqDeviceId);
    }

    // Mapper 를 통해 받은 세션 리스트의 ID 값을 이용해 상세 객체 리스트로 채움
    public List<ThrowSessionDto.DetailResDto> addList(List<ThrowSessionDto.DetailResDto> list,
                                                      Long reqUserId, String reqDeviceId) {
        List<ThrowSessionDto.DetailResDto> newList = new ArrayList<>();

        for (ThrowSessionDto.DetailResDto session : list) {
            newList.add(get(DefaultDto.DetailReqDto.builder()
                    .id(session.getId())
                    .build(), reqUserId, reqDeviceId));
        }

        return newList;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThrowSessionDto.DetailResDto> list(ThrowSessionDto.ListReqDto param,
                                                   Long reqUserId, String reqDeviceId) {
        // 클라이언트가 보낸 소유자 조건은 신뢰하지 않는다. 요청자 정보로 덮어써 본인 것만 조회되게 한다
        param.setUserId(reqUserId);
        param.setDeviceId(reqUserId == null ? reqDeviceId : null);

        if (reqUserId == null) {
            verifyDeviceId(reqDeviceId);
        }

        return addList(throwSessionMapper.list(param), reqUserId, reqDeviceId);
    }

    /* ── 내부 공통 ───────────────────────────────────────── */

    /**
     * 확정 지역 검증 + 정규화.
     *
     * UpdateReqDto 는 regionCode / regionName / lat / lng 를 클라이언트에게 그대로 받는다.
     * 아무 검증 없이 반영하면 한 번도 던지지 않고 원하는 지역으로 세션을 확정할 수 있고,
     * 그러면 이 서비스의 핵심인 "소외 지역 가중치 추첨" 이 통째로 무력화된다.
     *
     * 그래서 두 가지를 강제한다.
     *  1. 이름·좌표는 절대 믿지 않는다. 코드로 카탈로그에서 다시 찾아 덮어쓴다
     *  2. RANDOM 세션은 같은 세션에 "갈래(GO)" 로 남은 회차 기록이 있는 지역만 확정할 수 있다
     *
     * 회차 기록 쪽에서도 draw() 티켓으로 한 번 막지만(ThrowRoundServiceImpl.verifyDrawn),
     * 그쪽은 메모리라 재시작하면 사라진다. 여기서는 DB 에 남은 GO 기록을 근거로 삼아
     * 티켓이 없어도 확정 단계는 계속 막히도록 이중으로 둔다.
     *
     * SEARCH 세션은 지역 직접 검색이 기획상 허용된 경로라 카탈로그 존재 여부만 본다.
     */
    private void normalizeRegion(ThrowSessionDto.UpdateReqDto param, ThrowSession session) {
        if (isBlank(param.getRegionCode())) {
            // 지역을 건드리지 않는 수정(예: totalCount 만 갱신)은 그대로 통과시킨다.
            // 단, 코드 없이 좌표·이름만 보내는 요청은 기록을 어긋나게 하므로 막는다
            if (param.getLat() != null || param.getLng() != null || !isBlank(param.getRegionName())) {
                throw new InvalidRequestException("지역 코드 없이 지역 정보만 바꿀 수 없습니다.");
            }

            return;
        }

        RegionCatalog.Region region = findRegion(param.getRegionCode());

        if (ThrowSource.RANDOM.equals(session.getSource()) && !hasGoRound(session.getId(), region.code())) {
            throw new InvalidRequestException("던져서 뽑은 지역만 확정할 수 있습니다.");
        }

        param.setRegionCode(region.code());
        param.setRegionName(region.name());
        param.setLat(region.lat());
        param.setLng(region.lng());
    }

    /** 이 세션에서 해당 지역을 "갈래" 로 고른 회차가 실제로 남아 있는지 */
    private boolean hasGoRound(Long throwSessionId, String regionCode) {
        return throwRoundRepository.findByThrowSessionIdAndDeletedFalse(throwSessionId).stream()
                .anyMatch(round -> ThrowChoice.GO.equals(round.getChoice())
                        && regionCode.equals(round.getRegionCode()));
    }

    /** 카탈로그에 없는 코드는 받지 않는다. 임의 좌표로 세션을 만드는 경로를 막는다 */
    private RegionCatalog.Region findRegion(String regionCode) {
        RegionCatalog.Region region = regionCatalog.find(regionCode);

        if (region == null) {
            throw new InvalidRequestException("알 수 없는 지역 코드입니다 : " + regionCode);
        }

        return region;
    }

    // 수정·삭제처럼 엔티티가 필요한 작업에서 쓰는 조회 + 소유권 검증
    private ThrowSession getEntity(Long id, Long reqUserId, String reqDeviceId) {
        ThrowSession session = throwSessionRepository.findById(id)
                .orElseThrow(() -> new NoMatchingDataException("id : " + id));

        if (Boolean.TRUE.equals(session.getDeleted())) {
            throw new NoMatchingDataException("id : " + id);
        }

        verifyOwner(session.getUserId(), session.getDeviceId(), reqUserId, reqDeviceId);

        return session;
    }

    /**
     * 소유권 검증.
     * 주인이 정해진 세션은 userId 로, 아직 익명인 세션은 deviceId 로 판단한다.
     * 익명 세션을 userId 로 통과시키면 남의 기기 세션을 가져갈 수 있으므로 두 경로를 섞지 않는다.
     */
    private void verifyOwner(Long ownerUserId, String ownerDeviceId, Long reqUserId, String reqDeviceId) {
        if (ownerUserId != null) {
            if (!ownerUserId.equals(reqUserId)) {
                throw new AccessDeniedException("본인의 세션만 접근할 수 있습니다.");
            }
            return;
        }

        if (isBlank(reqDeviceId) || !reqDeviceId.equals(ownerDeviceId)) {
            throw new AccessDeniedException("본인의 세션만 접근할 수 있습니다.");
        }
    }

    private void verifyDeviceId(String reqDeviceId) {
        if (isBlank(reqDeviceId)) {
            throw new InvalidRequestException("기기 식별자(X-Device-Id 헤더)가 필요합니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
