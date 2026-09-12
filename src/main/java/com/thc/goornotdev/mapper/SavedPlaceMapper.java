package com.thc.goornotdev.mapper;

import com.thc.goornotdev.DTO.SavedPlaceDto;

import java.util.List;

public interface SavedPlaceMapper {
    SavedPlaceDto.DetailResDto detail(Long id);

    List<SavedPlaceDto.DetailResDto> list(SavedPlaceDto.ListReqDto param);
}
