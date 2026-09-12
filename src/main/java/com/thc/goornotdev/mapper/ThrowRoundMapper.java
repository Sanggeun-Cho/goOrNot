package com.thc.goornotdev.mapper;

import com.thc.goornotdev.DTO.ThrowRoundDto;

import java.util.List;

public interface ThrowRoundMapper {
    ThrowRoundDto.DetailResDto detail(Long id);

    List<ThrowRoundDto.DetailResDto> list(ThrowRoundDto.ListReqDto param);

    /** 같은 세션에서 이미 뽑힌 좌표 목록 (다음 랜덤 후보에서 제외) */
    List<ThrowRoundDto.CoordinateResDto> excludedCoordinates(Long throwSessionId);
}
