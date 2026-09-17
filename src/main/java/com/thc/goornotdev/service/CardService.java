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
}
