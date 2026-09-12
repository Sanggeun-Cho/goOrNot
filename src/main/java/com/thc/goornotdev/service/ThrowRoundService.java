package com.thc.goornotdev.service;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowRoundDto;

import java.util.List;

/**
 * 던지기 회차 기록.
 *
 * 세션과 마찬가지로 비로그인 상태에서도 쓰이므로 reqUserId 와 reqDeviceId 를 함께 받는다.
 * 회차 자체에는 소유자 정보가 없고, 항상 상위 세션의 소유권으로 접근을 판단한다.
 *
 * update / delete 가 없다.
 *   - update : 회차는 append-only 기록이라 수정 대상이 아니다.
 *   - delete : 세션 삭제 시 ThrowSessionService 가 순회하며 지운다. 단독 삭제 API 는 두지 않는다.
 */
public interface ThrowRoundService {
    /**
     * 회차 기록 생성
     * roundNo 는 서버가 직전 회차 + 1 로 채운다.
     * @param param 회차 정보 (throwSessionId, regionCode, lat, lng, choice)
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return DB에 저장된 회차의 고유 ID
     */
    DefaultDto.CreateResDto create(ThrowRoundDto.CreateReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 회차 상세 정보
     * @param param 조회할 회차 ID
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return 회차 상세 데이터
     */
    ThrowRoundDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 회차 목록 조회 (세션 단위)
     * @param param 필터 검색 조건 (throwSessionId 필수, choice)
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return 회차 상세 데이터 리스트
     */
    List<ThrowRoundDto.DetailResDto> list(ThrowRoundDto.ListReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 다음 랜덤 후보에서 제외할 좌표 목록
     * 같은 세션에서 이미 뽑힌 지역을 또 뽑지 않도록 프론트가 블랙리스트로 사용한다.
     * @param param 대상 세션 ID
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return 이미 뽑힌 좌표 리스트
     */
    List<ThrowRoundDto.CoordinateResDto> excludedCoordinates(DefaultDto.DetailReqDto param,
                                                             Long reqUserId, String reqDeviceId);
}
