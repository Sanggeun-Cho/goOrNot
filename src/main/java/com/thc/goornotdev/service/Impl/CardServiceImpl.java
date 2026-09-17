package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.CardDto;
import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.PlaceDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.domain.ThrowCategory;
import com.thc.goornotdev.domain.ThrowStatus;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.service.CardService;
import com.thc.goornotdev.service.PlaceService;
import com.thc.goornotdev.service.ThrowSessionService;
import com.thc.goornotdev.util.ExpiringStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RequiredArgsConstructor
@Service
public class CardServiceImpl implements CardService {

    /**
     * 후보 풀 크기.
     *
     * 가까운 순으로 10곳만 남기고 그 안에서 랜덤으로 고른다.
     * 넓히면 다양해지지만 확정된 지역에서 멀어져 "그 동네에 갔다" 는 느낌이 옅어지고,
     * 좁히면 리롤을 두 번만 눌러도 후보가 바닥난다.
     */
    private static final int POOL_SIZE = 10;

    /** 한 번에 깔아주는 카드 수 */
    private static final int CARD_COUNT = 3;

    /**
     * 풀 유지 시간.
     * 카테고리를 고르고 카드를 보다가 지도를 열어보는 정도의 시간을 잡았다.
     * 지역 추첨 티켓과 같은 30분이지만 서로 다른 저장소다 — 수명이 같은 건 우연이다
     */
    private static final Duration POOL_TTL = Duration.ofMinutes(30);

    /**
     * 풀 상한.
     *
     * ExpiringStore 기본값(10,000)을 쓰지 않는다.
     * 추첨 티켓은 엔트리 하나가 문자열 한 개지만, 여기는 장소 DTO 10개 묶음이라
     * 같은 개수여도 메모리 무게가 두 자릿수 배 차이가 난다.
     * 동시 진행 세션이 2,000개를 넘길 규모면 인메모리 캐시 자체를 다시 봐야 한다
     */
    private static final int MAX_POOLS = 2_000;

    /**
     * 후보를 뽑을 때 쓰는 정렬.
     *
     * S = 거리순 + 대표이미지가 있는 것만.
     * 거리순인 이유는 확정된 지역 안에서 고른다는 전제를 지키기 위해서고,
     * 이미지 보장인 이유는 카드가 사진 중심 UI 라 이미지 없는 장소가 섞이면 빈 카드가 되기 때문이다.
     *
     * 주의: 이 값은 위치기반 조회에서만 유효하다. 지역기반 조회에는 거리 개념이 없어
     * E/S 를 보내면 파라미터 오류가 난다.
     */
    private static final String POOL_ARRANGE = "S";

    private final PlaceService placeService;

    // 카드에는 소유자 정보가 없다. 상위 세션 조회가 곧 소유권 검증이라 세션 서비스를 주입한다
    private final ThrowSessionService throwSessionService;

    /**
     * 카테고리별 후보 풀 (key = 세션 ID + 카테고리).
     *
     * 왜 카테고리까지 키에 넣는가:
     * 세션 하나가 여러 카테고리를 오갈 수 있고, 각 카테고리는 리롤 횟수를 따로 센다.
     * 세션 ID 만으로 키를 잡으면 카테고리를 바꿀 때마다 앞의 카드가 지워져
     * 되돌아왔을 때 리롤이 되살아난다.
     *
     * 왜 DB 가 아닌가:
     * 카드 세트는 사용자가 하트를 누르는 순간 SavedPlace 로 남는다.
     * 그 전까지는 "고민 중" 상태일 뿐이라 유실돼도 카드를 다시 받으면 그만이다.
     *
     * 한계: 인스턴스 로컬이다. 서버를 늘리면 리롤 요청이 다른 인스턴스로 가서
     * 풀을 못 찾고 새 카드가 깔린다. 세션 어피니티나 외부 저장소가 필요해지는 지점이다.
     */
    private final ExpiringStore<String, CardPool> pools = new ExpiringStore<>(POOL_TTL, MAX_POOLS);

    @Override
    public CardDto.SetResDto deal(CardDto.DealReqDto param, Long reqUserId, String reqDeviceId) {
        ThrowCategory category = ThrowCategory.from(param.getCategory());
        ThrowSessionDto.DetailResDto session = verifyConfirmedSession(param.getThrowSessionId(), reqUserId, reqDeviceId);

        String key = poolKey(session.getId(), category);
        CardPool pool = pools.get(key);

        // 조회와 저장 사이가 원자적이지 않다. 같은 카테고리를 빠르게 두 번 누르면
        // TourAPI 가 두 번 호출되고 한쪽 풀은 버려진다.
        // 잠금으로 막지 않는 이유는 그 사이에 외부 호출이 끼어 있기 때문이다 —
        // 응답이 5초까지 걸릴 수 있는 구간을 잠그면 같은 버킷의 다른 세션까지 함께 멈춘다.
        // 낭비되는 건 중복 클릭 한 번 분량이라, 화면에서 버튼을 잠그는 쪽이 싸다
        if (pool == null) {
            pool = createPool(session.getRegionCode(), category);
            pools.put(key, pool);
        }

        return toSet(session, category, key, pool);
    }

    @Override
    public CardDto.SetResDto reroll(CardDto.RerollReqDto param, Long reqUserId, String reqDeviceId) {
        ThrowCategory category = ThrowCategory.from(param.getCategory());
        ThrowSessionDto.DetailResDto session = verifyConfirmedSession(param.getThrowSessionId(), reqUserId, reqDeviceId);

        String key = poolKey(session.getId(), category);
        CardPool pool = pools.get(key);

        // 풀이 없으면 카드도 없다. 여기서 새로 뽑아주면 리롤 횟수가 조용히 초기화되므로
        // 만들지 않고 "다시 받아라" 로 돌려보낸다
        if (pool == null) {
            throw new InvalidRequestException("카드가 만료되었습니다. 카드를 다시 받아주세요.");
        }

        if (param.getSlot() == null) {
            throw new InvalidRequestException("바꿀 카드 슬롯이 필요합니다.");
        }

        pool.reroll(param.getSlot());

        return toSet(session, category, key, pool);
    }

    /* ── 내부 ────────────────────────────────────────────── */

    /**
     * 후보 풀 생성. 이 메서드에서만 TourAPI 가 호출된다.
     *
     * 결과가 0건이어도 풀을 만들어 저장한다.
     * "이 지역엔 이 카테고리 장소가 없다" 는 것도 확인된 사실이라,
     * 같은 카테고리를 다시 눌렀을 때 외부 호출을 반복할 이유가 없다.
     */
    private CardPool createPool(String regionCode, ThrowCategory category) {
        PlaceDto.ListResDto result = placeService.list(PlaceDto.ListReqDto.builder()
                .regionCode(regionCode)
                .contentTypeId(category.getContentTypeId())
                .arrange(POOL_ARRANGE)
                .numOfRows(POOL_SIZE)
                .pageNo(1)
                .build());

        List<PlaceDto.DetailResDto> candidates = (result.getPlaces() == null)
                ? List.of() : result.getPlaces();

        if (candidates.size() < CARD_COUNT) {
            // 카드가 3장이 안 되는 상황은 화면에서 티가 나므로, 원인을 찾을 수 있게 남긴다
            log.info("카드 후보 부족 : 지역 {} / 카테고리 {} / 후보 {}건",
                    regionCode, category.name(), candidates.size());
        }

        return new CardPool(candidates, result.getTotalCount());
    }

    /**
     * 상위 세션 조회 + 소유권 + 지역 확정 여부 검증.
     *
     * 카드는 지역이 확정된 뒤의 단계다. 확정 전 세션으로 카드를 받아가면
     * 던지기를 끝내지 않고도 아무 지역의 추천을 얻는 우회가 된다.
     */
    private ThrowSessionDto.DetailResDto verifyConfirmedSession(Long throwSessionId, Long reqUserId, String reqDeviceId) {
        if (throwSessionId == null) {
            throw new InvalidRequestException("세션 ID 가 필요합니다.");
        }

        ThrowSessionDto.DetailResDto session = throwSessionService.detail(DefaultDto.DetailReqDto.builder()
                .id(throwSessionId)
                .build(), reqUserId, reqDeviceId);

        if (session == null) {
            throw new AccessDeniedException("본인의 세션만 접근할 수 있습니다.");
        }

        if (!ThrowStatus.CONFIRMED.equals(session.getStatus())) {
            throw new InvalidRequestException("아직 지역이 확정되지 않았습니다.");
        }

        // 확정 상태인데 지역이 비어 있으면 세션 데이터가 깨진 것이다. 정상 흐름에서는 나오지 않는다
        if (session.getRegionCode() == null || session.getRegionCode().isBlank()) {
            throw new InvalidRequestException("세션에 확정된 지역이 없습니다.");
        }

        return session;
    }

    private String poolKey(Long throwSessionId, ThrowCategory category) {
        return throwSessionId + ":" + category.name();
    }

    private CardDto.SetResDto toSet(ThrowSessionDto.DetailResDto session, ThrowCategory category,
                                    String key, CardPool pool) {
        Duration remaining = pools.remaining(key);

        return CardDto.SetResDto.builder()
                .throwSessionId(session.getId())
                .regionCode(session.getRegionCode())
                .regionName(session.getRegionName())
                .category(category.name())
                .categoryLabel(category.getLabel())
                .poolSize(pool.size())
                .totalCount(pool.totalCount())
                .expiresInSeconds((remaining == null) ? 0 : remaining.toSeconds())
                .cards(pool.snapshot())
                .build();
    }

    /**
     * 한 카테고리의 후보 목록 + 지금 깔려 있는 카드 상태.
     *
     * record 가 아니라 클래스인 이유는 리롤이 상태를 바꾸기 때문이다.
     *
     * 동기화가 필요한 이유:
     * 리롤 버튼 더블클릭이나 요청 재시도로 같은 슬롯에 요청이 겹칠 수 있다.
     * 검사와 변경 사이가 벌어지면 "1회 제한" 을 통과한 요청 두 개가 모두 카드를 바꿔
     * 사실상 2회가 된다. 읽기(snapshot)까지 함께 잠그는 건 슬롯 배열과 리롤 배열이
     * 반쯤 갱신된 상태로 화면에 나가지 않게 하기 위해서다.
     */
    private static final class CardPool {
        private final List<PlaceDto.DetailResDto> candidates;
        private final Integer totalCount;

        /** 슬롯별로 어떤 후보를 깔았는지 (candidates 의 인덱스) */
        private final int[] dealt;

        /** 슬롯별 리롤 사용 여부 */
        private final boolean[] rerolled;

        private CardPool(List<PlaceDto.DetailResDto> candidates, Integer totalCount) {
            this.candidates = List.copyOf(candidates);
            this.totalCount = totalCount;

            // 후보가 3개 미만이면 카드도 그만큼만 깐다. 같은 장소를 두 칸에 채우지는 않는다
            int cardCount = Math.min(CARD_COUNT, this.candidates.size());

            this.dealt = new int[cardCount];
            this.rerolled = new boolean[cardCount];

            List<Integer> order = new ArrayList<>();

            for (int index = 0; index < this.candidates.size(); index++) {
                order.add(index);
            }

            // 거리순으로 받아온 목록을 섞어서 앞에서부터 깐다.
            // 보안용 난수가 아니라 게임성용이라 ThreadLocalRandom 이면 충분하다
            Collections.shuffle(order, ThreadLocalRandom.current());

            for (int slot = 0; slot < cardCount; slot++) {
                dealt[slot] = order.get(slot);
            }
        }

        private int size() {
            return candidates.size();
        }

        private Integer totalCount() {
            return totalCount;
        }

        private synchronized void reroll(int slot) {
            if (slot < 0 || slot >= dealt.length) {
                throw new InvalidRequestException("없는 카드 슬롯입니다 : " + slot);
            }

            if (rerolled[slot]) {
                throw new InvalidRequestException("이미 한 번 바꾼 카드입니다.");
            }

            List<Integer> spare = spareIndexes();

            // 여기서 리롤을 소모하지 않는 게 중요하다.
            // 바꿀 게 없는데 기회만 사라지면 사용자는 그냥 버그로 받아들인다
            if (spare.isEmpty()) {
                throw new InvalidRequestException("이 지역에서 더 보여줄 장소가 없습니다.");
            }

            dealt[slot] = spare.get(ThreadLocalRandom.current().nextInt(spare.size()));
            rerolled[slot] = true;
        }

        /** 지금 깔려 있지 않은 후보들. 리롤로 같은 장소가 두 칸에 나오지 않게 한다 */
        private List<Integer> spareIndexes() {
            List<Integer> spare = new ArrayList<>();

            for (int index = 0; index < candidates.size(); index++) {
                boolean onTable = false;

                for (int dealtIndex : dealt) {
                    if (dealtIndex == index) {
                        onTable = true;

                        break;
                    }
                }

                if (!onTable) {
                    spare.add(index);
                }
            }

            return spare;
        }

        private synchronized List<CardDto.DetailResDto> snapshot() {
            List<CardDto.DetailResDto> cards = new ArrayList<>();

            // 깔린 카드는 서로 다른 후보라, 여분이 있는지는 개수 비교만으로 알 수 있다.
            // 아직 안 쓴 리롤이라도 바꿀 후보가 없으면 눌리면 안 되므로 함께 따진다
            boolean hasSpare = candidates.size() > dealt.length;

            for (int slot = 0; slot < dealt.length; slot++) {
                cards.add(CardDto.DetailResDto.builder()
                        .slot(slot)
                        .rerollable(!rerolled[slot] && hasSpare)
                        .place(candidates.get(dealt[slot]))
                        .build());
            }

            return cards;
        }
    }
}
