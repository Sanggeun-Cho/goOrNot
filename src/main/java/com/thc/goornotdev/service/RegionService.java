package com.thc.goornotdev.service;

import com.thc.goornotdev.DTO.RegionDto;

import java.util.List;

public interface RegionService {
    /** 검색어로 시군구를 찾는다. 지역 직접 검색(source=SEARCH) 진입에 쓰인다 */
    List<RegionDto.DetailResDto> list(RegionDto.ListReqDto param);
}
