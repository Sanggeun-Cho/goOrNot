package com.thc.goornotdev.service;

import com.thc.goornotdev.DTO.DefaultDto;
import com.thc.goornotdev.DTO.SavedPlaceDto;

import java.util.List;

/**
 * 하트로 저장한 장소.
 *
 * 저장 장소는 회원 계정에 귀속되므로 로그인 필수다. 그래서 소유권 판단은 reqUserId 하나로 충분하다.
 * 다만 생성 시점에는 상위 세션이 아직 익명(userId == null)일 수 있어
 * create 만 예외적으로 reqDeviceId 를 함께 받아 세션 소유권을 확인한다.
 *
 * update 가 없다. 저장 장소는 저장 / 해제만 있고 내용을 고칠 일이 없다.
 */
public interface SavedPlaceService {
    /**
     * 장소 저장 (하트)
     * 같은 사용자가 같은 contentId 를 중복 저장할 수 없다.
     * @param param 장소 정보 (throwSessionId, contentId, category, placeName, address, lat, lng)
     * @param reqUserId 요청한 사용자 ID (필수)
     * @param reqDeviceId 요청한 기기 ID (세션이 아직 익명일 때 소유권 확인용)
     * @return DB에 저장된 장소의 고유 ID
     */
    DefaultDto.CreateResDto create(SavedPlaceDto.CreateReqDto param, Long reqUserId, String reqDeviceId);

    /**
     * 장소 저장 해제 (Soft Delete)
     * @param param 해제할 장소 ID
     * @param reqUserId 요청한 사용자 ID (필수)
     */
    void delete(DefaultDto.DetailReqDto param, Long reqUserId);

    /**
     * 저장 장소 상세 정보
     * @param param 조회할 장소 ID
     * @param reqUserId 요청한 사용자 ID (필수)
     * @return 저장 장소 상세 데이터
     */
    SavedPlaceDto.DetailResDto detail(DefaultDto.DetailReqDto param, Long reqUserId);

    /**
     * 저장 장소 목록 조회 (본인 것만)
     * throwSessionId 를 주면 세션 상세용, 주지 않으면 마이페이지용 전체 목록이다.
     * @param param 필터 검색 조건 (throwSessionId, category)
     * @param reqUserId 요청한 사용자 ID (필수)
     * @return 저장 장소 상세 데이터 리스트
     */
    List<SavedPlaceDto.DetailResDto> list(SavedPlaceDto.ListReqDto param, Long reqUserId);
}
