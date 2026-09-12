package com.thc.goornotdev.mapper;

import com.thc.goornotdev.DTO.UserDto;

import java.util.List;

public interface UserMapper {
    UserDto.DetailResDto detail(Long id);

    List<UserDto.DetailResDto> list(UserDto.ListReqDto param);
}
