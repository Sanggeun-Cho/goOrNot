package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowRoundDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.domain.ThrowRound;
import com.thc.goornotdev.domain.ThrowStatus;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.exception.NoMatchingDataException;
import com.thc.goornotdev.mapper.ThrowRoundMapper;
import com.thc.goornotdev.repository.ThrowRoundRepository;
import com.thc.goornotdev.service.ThrowRoundService;
import com.thc.goornotdev.service.ThrowSessionService;
import com.thc.goornotdev.util.RegionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class ThrowRoundServiceImpl implements ThrowRoundService {
    private final ThrowRoundRepository throwRoundRepository;
    private final ThrowRoundMapper throwRoundMapper;

    // 회차에는 소유자 정보가 없다. 상위 세션 조회가 곧 소유권 검증이라 세션 서비스를 주입한다
    private final ThrowSessionService throwSessionService;

    // 지역 후보 목록(resources/regions.json)을 들고 있는 인메모리 카탈로그
    private final RegionCatalog regionCatalog;

    @Override
    @Transactional
    public DefaultDto.CreateResDto create(ThrowRoundDto.CreateReqDto param, Long reqUserId, String reqDeviceId) {
        ThrowSessionDto.DetailResDto session = verifySession(param.getThrowSessionId(), reqUserId, reqDeviceId);

        // 이미 확정된 세션에 회차를 덧붙이면 totalCount 와 기록이 어긋난다
        if (ThrowStatus.CONFIRMED.equals(session.getStatus())) {
            throw new InvalidRequestException("이미 확정된 세션에는 회차를 추가할 수 없습니다.");
        }

        ThrowRound newRound = throwRoundRepository.save(param.toEntity(nextRoundNo(session.getId())));

        return newRound.toCreateResDto();
    }

    // Mapper 를 이용한 회차 조회 함수. 소유권 검증을 여기서 한 번에 처리한다
    public ThrowRoundDto.DetailResDto get(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId) {
        ThrowRoundDto.DetailResDto round = throwRoundMapper.detail(param.getId());

        if (round == null) {
            throw new NoMatchingDataException("id : " + param.getId());
        }

        verifySession(round.getThrowSessionId(), reqUserId, reqDeviceId);

        return round;
    }

    @Override
    @Transactional(readOnly = true)
    public ThrowRoundDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("조회할 회차 ID 가 필요합니다.");
        }

        return get(param, reqUserId, reqDeviceId);
    }

    /**
     * Mapper 를 통해 받은 회차 리스트의 ID 값을 이용해 상세 객체 리스트로 채움.
     *
     * 세션 소유권은 list() 에서 이미 한 번 확인했으므로 여기서는 다시 검증하지 않는다.
     * 같은 세션의 회차만 조회된 상태라 건당 재검증은 같은 결과를 반복하는 비용일 뿐이다.
     */
    public List<ThrowRoundDto.DetailResDto> addList(List<ThrowRoundDto.DetailResDto> list) {
        List<ThrowRoundDto.DetailResDto> newList = new ArrayList<>();

        for (ThrowRoundDto.DetailResDto round : list) {
            ThrowRoundDto.DetailResDto detail = throwRoundMapper.detail(round.getId());

            if (detail != null) {
                newList.add(detail);
            }
        }

        return newList;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThrowRoundDto.DetailResDto> list(ThrowRoundDto.ListReqDto param, Long reqUserId, String reqDeviceId) {
        verifySession(param.getThrowSessionId(), reqUserId, reqDeviceId);

        return addList(throwRoundMapper.list(param));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThrowRoundDto.CoordinateResDto> excludedCoordinates(DefaultDto.DetailReqDto param,
                                                                    Long reqUserId, String reqDeviceId) {
        verifySession(param.getId(), reqUserId, reqDeviceId);

        return throwRoundMapper.excludedCoordinates(param.getId());
    }

    /**
     * 지역 추첨.
     *
     * 왜 이 클래스인가:
     * 추첨의 핵심 입력이 "같은 세션에서 이미 뽑힌 지역"인데, 그 목록을 꺼내는
     * {@link #excludedCoordinates} 와 소유권 검증 {@code verifySession} 이 여기 있다.
     * 세션 서비스에 두면 회차 Mapper 를 다시 끌어와야 해서 책임이 흩어진다.
     *
     * 결과를 DB 에 쓰지 않는다. 확정은 사용자가 "갈래/말래" 를 고른 뒤
     * create() 로 기록되며, 추첨 자체는 부수효과가 없는 읽기 작업이다.
     */
    @Override
    @Transactional(readOnly = true)
    public ThrowRoundDto.DrawResDto draw(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId) {
        if (param.getId() == null) {
            throw new InvalidRequestException("추첨할 세션 ID 가 필요합니다.");
        }

        ThrowSessionDto.DetailResDto session = verifySession(param.getId(), reqUserId, reqDeviceId);

        if (ThrowStatus.CONFIRMED.equals(session.getStatus())) {
            throw new InvalidRequestException("이미 확정된 세션에서는 다시 추첨할 수 없습니다.");
        }

        // 말래(재던지기) 블랙리스트. 이미 뽑힌 지역은 이번 추첨에서 가중치 0 과 같은 효과로 빠진다
        List<String> excludedCodes = throwRoundMapper.excludedCoordinates(session.getId()).stream()
                .map(ThrowRoundDto.CoordinateResDto::getRegionCode)
                .filter(Objects::nonNull)
                .toList();

        RegionCatalog.Region region = regionCatalog.draw(excludedCodes);

        return ThrowRoundDto.DrawResDto.builder()
                .regionCode(region.code())
                .regionName(region.name())
                .lat(region.lat())
                .lng(region.lng())
                .build();
    }

    /* ── 내부 공통 ───────────────────────────────────────── */

    /**
     * 상위 세션 조회 + 소유권 검증.
     * 세션 서비스의 detail() 이 소유자가 아니면 AccessDeniedException 을 던지므로 그대로 위임한다.
     */
    private ThrowSessionDto.DetailResDto verifySession(Long throwSessionId, Long reqUserId, String reqDeviceId) {
        if (throwSessionId == null) {
            throw new InvalidRequestException("세션 ID 가 필요합니다.");
        }

        ThrowSessionDto.DetailResDto session = throwSessionService.detail(DefaultDto.DetailReqDto.builder()
                .id(throwSessionId)
                .build(), reqUserId, reqDeviceId);

        if (session == null) {
            throw new AccessDeniedException("본인의 세션만 접근할 수 있습니다.");
        }

        return session;
    }

    /**
     * 다음 회차 번호.
     * 삭제된 회차까지 포함해 최댓값 + 1 로 잡는다. 남은 개수로 세면 번호가 겹칠 수 있다.
     */
    private Integer nextRoundNo(Long throwSessionId) {
        return throwRoundRepository.findTopByThrowSessionIdOrderByRoundNoDesc(throwSessionId)
                .map(round -> round.getRoundNo() + 1)
                .orElse(1);
    }
}
