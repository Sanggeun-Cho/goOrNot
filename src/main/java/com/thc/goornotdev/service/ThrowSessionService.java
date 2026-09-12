package com.thc.goornotdev.service;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.ThrowSessionDto;

import java.util.List;

/**
 * 던지기 세션.
 *
 * 로그인 전에도 세션을 만들 수 있어야 해서 요청자를 userId 하나로 특정할 수 없다.
 * 그래서 User 도메인과 달리 모든 메서드가 reqUserId 와 reqDeviceId 를 함께 받는다.
 *   - 로그인 상태  : reqUserId 로 소유권 판단
 *   - 비로그인 상태 : reqDeviceId(X-Device-Id 헤더)로 소유권 판단
 */
public interface ThrowSessionService {
    /**
     * 세션 생성
     * @param param 세션 정보 (source, totalCount, regionCode, regionName, lat, lng)
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return DB에 저장된 세션의 고유 ID
     */
    DefaultDto.CreateResDto create(ThrowSessionDto.CreateReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 세션 정보 수정 (RANDOM 확정 등)
     * @param param 수정할 세션 ID 와 수정 항목 (status, totalCount, regionCode, regionName, lat, lng)
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     */
    void update(ThrowSessionDto.UpdateReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 로그인 완료 시 익명 세션을 회원 계정에 연결
     * 로그인을 트리거한 세션 하나만 대상이며, 같은 기기의 다른 세션은 건드리지 않는다.
     * @param param 연결할 세션 ID
     * @param reqUserId 요청한 사용자 ID (필수)
     * @param reqDeviceId 요청한 기기 ID
     */
    void linkUser(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 세션 삭제 (Soft Delete)
     * 하위 ThrowRound / SavedPlace 도 함께 Soft Delete 한다.
     * @param param 삭제할 세션 ID
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     */
    void delete(ThrowSessionDto.UpdateReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 세션 상세 정보
     * @param param 조회할 세션 ID
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return 세션 상세 데이터
     */
    ThrowSessionDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 세션 목록 조회 (본인 것만)
     * @param param 필터 검색 조건 (source, status)
     * @param reqUserId 요청한 사용자 ID (비로그인이면 null)
     * @param reqDeviceId 요청한 기기 ID
     * @return 세션 상세 데이터 리스트
     */
    List<ThrowSessionDto.DetailResDto> list(ThrowSessionDto.ListReqDto param, Long reqUserId, String reqDeviceId);
}
