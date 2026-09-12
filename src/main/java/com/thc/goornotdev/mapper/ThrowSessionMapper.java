package com.thc.goornotdev.mapper;

import com.thc.goornotdev.DTO.ThrowSessionDto;

import java.util.List;

public interface ThrowSessionMapper {
    ThrowSessionDto.DetailResDto detail(Long id);

    List<ThrowSessionDto.DetailResDto> list(ThrowSessionDto.ListReqDto param);
}
