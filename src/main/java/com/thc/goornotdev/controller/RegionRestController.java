package com.thc.goornotdev.controller;

import com.thc.goornotdev.DTO.RegionDto;
import com.thc.goornotdev.service.RegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 지역(시군구) 조회.
 *
 * 다른 컨트롤러와 달리 getUserId 헬퍼가 없다.
 * 행정구역 목록은 누구에게나 같은 공개 데이터라 요청자를 식별할 이유가 없다.
 * 소유권 개념이 없으니 deviceId 도 받지 않는다.
 *
 * 던지기와 달리 결과가 매번 같고 서버 상태도 바뀌지 않으므로 GET 이 맞다.
 */
@RequiredArgsConstructor
@RequestMapping("/api/region")
@RestController
public class RegionRestController {

    private final RegionService regionService;

    @PreAuthorize("permitAll()")
    @GetMapping("/list")
    public ResponseEntity<List<RegionDto.DetailResDto>> list(@Valid RegionDto.ListReqDto param) {
        return ResponseEntity.ok(regionService.list(param));
    }
}
