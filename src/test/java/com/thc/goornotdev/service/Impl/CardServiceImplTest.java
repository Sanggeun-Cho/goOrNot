package com.thc.goornotdev.service.Impl;

import com.thc.goornotdev.DTO.CardDto;
import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.PlaceDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;
import com.thc.goornotdev.domain.ThrowStatus;
import com.thc.goornotdev.exception.InvalidRequestException;
import com.thc.goornotdev.service.PlaceService;
import com.thc.goornotdev.service.ThrowSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 카드 서비스 단위 테스트.
 *
 * 여기서 지키려는 것은 "카드가 예쁘게 나오는가" 가 아니라
 * 리롤 제한을 우회할 수 있는 길이 열려 있지 않은가이다.
 *
 * TourAPI 는 호출하지 않는다. PlaceService 를 mock 으로 두고
 * "몇 번 불렀는지" 를 함께 검증해 일일 한도를 낭비하는 경로도 같이 막는다.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    private static final Long SESSION_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String DEVICE_ID = "device-uuid-1";
    private static final String REGION_CODE = "42210";
    private static final String CATEGORY = "FOOD";

    @Mock
    private PlaceService placeService;

    @Mock
    private ThrowSessionService throwSessionService;

    @InjectMocks
    private CardServiceImpl cardService;

    /* ── 픽스처 ──────────────────────────────────────────── */

    private ThrowSessionDto.DetailResDto session(ThrowStatus status, String regionCode) {
        return ThrowSessionDto.DetailResDto.builder()
                .id(SESSION_ID)
                .userId(USER_ID)
                .deviceId(DEVICE_ID)
                .status(status)
                .regionCode(regionCode)
                .regionName("강원특별자치도 속초시")
                .build();
    }

    private ThrowSessionDto.DetailResDto confirmedSession() {
        return session(ThrowStatus.CONFIRMED, REGION_CODE);
    }

    /** 이름으로 서로 구분되는 장소 n 건 */
    private PlaceDto.ListResDto places(int count) {
        List<PlaceDto.DetailResDto> places = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            places.add(PlaceDto.DetailResDto.builder()
                    .contentId("content-" + index)
                    .contentTypeId("39")
                    .placeName("장소" + index)
                    .build());
        }

        return PlaceDto.ListResDto.builder()
                .regionCode(REGION_CODE)
                .totalCount(count)
                .places(places)
                .build();
    }

    private void givenConfirmedSessionWith(int placeCount) {
        given(throwSessionService.detail(any(DefaultDto.DetailReqDto.class), any(), any()))
                .willReturn(confirmedSession());
        given(placeService.list(any(PlaceDto.ListReqDto.class)))
                .willReturn(places(placeCount));
    }

    private CardDto.SetResDto deal() {
        return cardService.deal(CardDto.DealReqDto.builder()
                .throwSessionId(SESSION_ID)
                .category(CATEGORY)
                .build(), USER_ID, DEVICE_ID);
    }

    private CardDto.SetResDto reroll(int slot) {
        return cardService.reroll(CardDto.RerollReqDto.builder()
                .throwSessionId(SESSION_ID)
                .category(CATEGORY)
                .slot(slot)
                .build(), USER_ID, DEVICE_ID);
    }

    private List<String> contentIds(CardDto.SetResDto set) {
        return set.getCards().stream()
                .map(card -> card.getPlace().getContentId())
                .toList();
    }

    /* ── 카드 깔기 ───────────────────────────────────────── */

    @Test
    @DisplayName("카드를 받으면 서로 다른 3장이 깔리고 전부 리롤 가능하다")
    void deal_dealsThreeDistinctCards() {
        givenConfirmedSessionWith(10);

        CardDto.SetResDto set = deal();

        assertThat(set.getCards()).hasSize(3);
        assertThat(contentIds(set)).doesNotHaveDuplicates();
        assertThat(set.getCards()).allMatch(CardDto.DetailResDto::isRerollable);
        assertThat(set.getCards()).extracting(CardDto.DetailResDto::getSlot)
                .containsExactly(0, 1, 2);
        assertThat(set.getPoolSize()).isEqualTo(10);
        assertThat(set.getRegionCode()).isEqualTo(REGION_CODE);
    }

    @Test
    @DisplayName("조회 조건은 클라이언트가 아니라 세션에 확정된 지역에서 만든다")
    void deal_usesConfirmedRegionNotClientInput() {
        givenConfirmedSessionWith(10);

        deal();

        ArgumentCaptor<PlaceDto.ListReqDto> captor = ArgumentCaptor.forClass(PlaceDto.ListReqDto.class);
        verify(placeService).list(captor.capture());

        PlaceDto.ListReqDto sent = captor.getValue();

        assertThat(sent.getRegionCode()).isEqualTo(REGION_CODE);
        // FOOD 의 contentTypeId. enum 을 고치면 여기가 먼저 깨지도록 숫자를 그대로 둔다
        assertThat(sent.getContentTypeId()).isEqualTo("39");
        // S = 거리순 + 대표이미지 보장. 이미지 없는 장소가 섞이면 카드가 빈다
        assertThat(sent.getArrange()).isEqualTo("S");
        assertThat(sent.getNumOfRows()).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 카테고리를 다시 열면 TourAPI 를 재호출하지 않고 같은 카드를 돌려준다")
    void deal_reusesPoolForSameCategory() {
        givenConfirmedSessionWith(10);

        List<String> first = contentIds(deal());
        List<String> second = contentIds(deal());

        assertThat(second).isEqualTo(first);
        verify(placeService, times(1)).list(any(PlaceDto.ListReqDto.class));
    }

    @Test
    @DisplayName("리롤을 쓴 뒤 카드를 다시 받아도 사용 이력이 남아 리롤이 부활하지 않는다")
    void deal_keepsRerollHistoryAcrossRedeal() {
        givenConfirmedSessionWith(10);

        deal();
        String rerolled = reroll(0).getCards().get(0).getPlace().getContentId();

        CardDto.SetResDto redealt = deal();

        assertThat(redealt.getCards().get(0).isRerollable()).isFalse();
        assertThat(redealt.getCards().get(0).getPlace().getContentId()).isEqualTo(rerolled);
        assertThat(redealt.getCards().get(1).isRerollable()).isTrue();
        assertThat(redealt.getCards().get(2).isRerollable()).isTrue();
    }

    @Test
    @DisplayName("후보가 3건보다 적으면 같은 장소를 채우지 않고 있는 만큼만 깐다")
    void deal_dealsFewerCardsWhenCandidatesShort() {
        givenConfirmedSessionWith(2);

        CardDto.SetResDto set = deal();

        assertThat(set.getCards()).hasSize(2);
        assertThat(contentIds(set)).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("후보가 깔린 카드 수와 같으면 바꿀 여분이 없어 리롤 버튼이 꺼진다")
    void deal_marksNotRerollableWhenNoSpare() {
        givenConfirmedSessionWith(3);

        CardDto.SetResDto set = deal();

        assertThat(set.getCards()).hasSize(3);
        assertThat(set.getCards()).noneMatch(CardDto.DetailResDto::isRerollable);
    }

    @Test
    @DisplayName("결과가 0건이어도 빈 풀을 캐시해 같은 카테고리를 다시 호출하지 않는다")
    void deal_cachesEmptyPool() {
        givenConfirmedSessionWith(0);

        assertThat(deal().getCards()).isEmpty();
        assertThat(deal().getCards()).isEmpty();

        verify(placeService, times(1)).list(any(PlaceDto.ListReqDto.class));
    }

    @Test
    @DisplayName("카테고리가 다르면 풀을 따로 만든다")
    void deal_keepsPoolPerCategory() {
        givenConfirmedSessionWith(10);

        deal();
        cardService.deal(CardDto.DealReqDto.builder()
                .throwSessionId(SESSION_ID)
                .category("STAY")
                .build(), USER_ID, DEVICE_ID);

        verify(placeService, times(2)).list(any(PlaceDto.ListReqDto.class));
    }

    /* ── 리롤 ────────────────────────────────────────────── */

    @Test
    @DisplayName("리롤하면 해당 슬롯만 바뀌고 이미 깔린 장소와 겹치지 않는다")
    void reroll_replacesOnlyTargetSlot() {
        givenConfirmedSessionWith(4);

        List<String> before = contentIds(deal());
        CardDto.SetResDto after = reroll(0);

        assertThat(contentIds(after)).doesNotHaveDuplicates();
        assertThat(contentIds(after).get(0)).isNotEqualTo(before.get(0));
        assertThat(contentIds(after).get(1)).isEqualTo(before.get(1));
        assertThat(contentIds(after).get(2)).isEqualTo(before.get(2));
        assertThat(after.getCards().get(0).isRerollable()).isFalse();
    }

    @Test
    @DisplayName("같은 슬롯을 두 번 리롤할 수 없다")
    void reroll_rejectsSecondRerollOnSameSlot() {
        givenConfirmedSessionWith(10);

        deal();
        reroll(0);

        assertThatThrownBy(() -> reroll(0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("이미 한 번 바꾼 카드");
    }

    @Test
    @DisplayName("슬롯마다 리롤을 따로 센다 - 0번을 썼어도 1번은 쓸 수 있다")
    void reroll_countsPerSlot() {
        givenConfirmedSessionWith(10);

        deal();
        reroll(0);

        CardDto.SetResDto set = reroll(1);

        assertThat(set.getCards().get(0).isRerollable()).isFalse();
        assertThat(set.getCards().get(1).isRerollable()).isFalse();
        assertThat(set.getCards().get(2).isRerollable()).isTrue();
    }

    @Test
    @DisplayName("풀이 없으면 새로 뽑아주지 않고 다시 받으라고 돌려보낸다")
    void reroll_rejectsWhenPoolMissing() {
        // 카드를 받은 적 없는 세션. 여기서 몰래 새 풀을 만들면 리롤 횟수가 초기화된다
        given(throwSessionService.detail(any(DefaultDto.DetailReqDto.class), any(), any()))
                .willReturn(confirmedSession());

        assertThatThrownBy(() -> reroll(0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("만료");

        verify(placeService, never()).list(any(PlaceDto.ListReqDto.class));
    }

    @Test
    @DisplayName("없는 슬롯 번호는 거부한다")
    void reroll_rejectsUnknownSlot() {
        givenConfirmedSessionWith(10);

        deal();

        assertThatThrownBy(() -> reroll(3))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("없는 카드 슬롯");
    }

    @Test
    @DisplayName("바꿀 후보가 없으면 카드를 건드리지 않고 400 으로 돌려보낸다")
    void reroll_rejectsWhenNoSpareAndKeepsCards() {
        givenConfirmedSessionWith(3);

        List<String> before = contentIds(deal());

        assertThatThrownBy(() -> reroll(0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("더 보여줄 장소가 없습니다");

        // 실패한 리롤이 카드 배치를 반쯤 바꿔놓고 나가면 안 된다
        assertThat(contentIds(deal())).isEqualTo(before);
    }

    /* ── 세션 검증 ───────────────────────────────────────── */

    @Test
    @DisplayName("지역이 확정되지 않은 세션은 카드를 받을 수 없다")
    void deal_rejectsUnconfirmedSession() {
        given(throwSessionService.detail(any(DefaultDto.DetailReqDto.class), any(), any()))
                .willReturn(session(ThrowStatus.IN_PROGRESS, null));

        assertThatThrownBy(this::deal)
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("아직 지역이 확정되지 않았습니다");

        // 던지기를 끝내지 않은 요청이 TourAPI 한도를 깎으면 안 된다
        verify(placeService, never()).list(any(PlaceDto.ListReqDto.class));
    }

    @Test
    @DisplayName("확정 상태인데 지역이 비어 있으면 거부한다")
    void deal_rejectsConfirmedSessionWithoutRegion() {
        given(throwSessionService.detail(any(DefaultDto.DetailReqDto.class), any(), any()))
                .willReturn(session(ThrowStatus.CONFIRMED, "  "));

        assertThatThrownBy(this::deal)
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("확정된 지역이 없습니다");

        verify(placeService, never()).list(any(PlaceDto.ListReqDto.class));
    }

    @Test
    @DisplayName("남의 세션이면 카드를 받을 수 없다")
    void deal_rejectsOtherUsersSession() {
        given(throwSessionService.detail(any(DefaultDto.DetailReqDto.class), any(), any()))
                .willReturn(null);

        assertThatThrownBy(this::deal)
                .isInstanceOf(AccessDeniedException.class);

        verify(placeService, never()).list(any(PlaceDto.ListReqDto.class));
    }

    @Test
    @DisplayName("알 수 없는 카테고리는 세션을 조회하기 전에 거부한다")
    void deal_rejectsUnknownCategory() {
        assertThatThrownBy(() -> cardService.deal(CardDto.DealReqDto.builder()
                .throwSessionId(SESSION_ID)
                .category("PARK")
                .build(), USER_ID, DEVICE_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("알 수 없는 카테고리");

        verify(throwSessionService, never())
                .detail(any(DefaultDto.DetailReqDto.class), any(), any());
        verify(placeService, never()).list(any(PlaceDto.ListReqDto.class));
    }
}
