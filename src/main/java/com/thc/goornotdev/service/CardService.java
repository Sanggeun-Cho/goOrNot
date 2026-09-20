package com.thc.goornotdev.service;

import com.thc.goornotdev.DTO.CardDto;

/**
 * 확정된 지역 + 카테고리로 뽑는 추천 카드.
 *
 * 지역 추첨(ThrowRoundService.draw)과 역할이 다르다.
 * 지역 추첨은 "어디로 갈지" 를 서비스가 정하는 단계라 가중치와 우회 차단이 걸려 있고,
 * 카드는 그렇게 정해진 지역 안에서 "무엇을 할지" 를 사용자가 고르는 단계다.
 * 그래서 카드 쪽 랜덤에는 가중치도, 결과를 대조하는 티켓도 없다.
 *
 * 두 메서드 모두 카테고리별 후보 풀을 공유한다.
 * 풀이 살아 있는 동안에는 TourAPI 를 다시 부르지 않는다 — 리롤을 누를 때마다
 * 외부 호출이 나가면 카드 세 장 고르는 사이에 일일 한도가 빠르게 녹는다.
 */
public interface CardService {
    /**
     * 카테고리의 카드 세트 조회.
     *
     * 처음 부르면 후보를 뽑아 세 장을 깔고, 풀이 살아 있는 동안 다시 부르면
     * 같은 세 장을 그대로 돌려준다. 되돌아왔다고 카드가 바뀌면
     * 리롤 1회 제한이 화면 이동만으로 풀려 무의미해진다.
     *
     * @param param       세션 ID + 카테고리 이름
     * @param reqUserId   요청한 사용자 ID
     * @param reqDeviceId 요청한 기기 ID
     * @return 카드 세트 (반경 안 장소가 적으면 3장 미만, 아예 없으면 빈 목록)
     */
    CardDto.SetResDto deal(CardDto.DealReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 카드 한 장 교체. 슬롯당 1회만 가능하다.
     *
     * 남은 후보가 없으면 리롤을 소모하지 않고 거절한다.
     * 바꿀 게 없는데 기회만 사라지면 사용자는 버그로 받아들인다.
     *
     * @param param       세션 ID + 카테고리 이름 + 슬롯 번호
     * @param reqUserId   요청한 사용자 ID
     * @param reqDeviceId 요청한 기기 ID
     * @return 교체가 반영된 카드 세트 전체
     */
    CardDto.SetResDto reroll(CardDto.RerollReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 이번 판을 끝낸다. 깔려 있던 카드와 남은 리롤을 버린다.
     *
     * [2026-09-19 확정] 카테고리는 일회성이다.
     * "오늘 뭐하지 → 관광지 → 리롤 → 여기 갈래" 한 번이 한 판이고, 다음 날 같은 카테고리를
     * 다시 열면 새 카드로 다시 추천받아야 한다. 풀이 30분 살아 있는 건 "고민하는 동안
     * 카드가 안 바뀐다" 를 위한 것이지 판을 이어가기 위한 게 아니다.
     *
     * 그래서 "여기 간다" 가 켜지는 순간 그 카테고리의 풀을 버린다. 다음 deal 은 새 풀을 만든다.
     *
     * 검증하지 않는다. 이건 캐시 무효화라 대상이 없거나 카테고리 문자열을 못 알아봐도
     * 조용히 넘어간다 — 여기서 예외를 던지면 정상적으로 끝난 저장이 400 으로 뒤집힌다.
     *
     * @param throwSessionId 세션 ID
     * @param category       카테고리 이름 (SavedPlace 에 저장된 자유 문자열이라 파싱 실패를 허용한다)
     */
    void endRound(Long throwSessionId, String category);
}
