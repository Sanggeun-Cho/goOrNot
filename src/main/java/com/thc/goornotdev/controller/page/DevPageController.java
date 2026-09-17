package com.thc.goornotdev.controller.page;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 개발용 임시 화면 컨트롤러. 제출 전에 삭제한다.
 *
 * application.yml 에 external.tourapi.dev-tools=true 가 있을 때만 빈으로 등록된다.
 * 값이 없으면 이 컨트롤러 자체가 없는 것과 같아 /dev/** 는 404 가 된다
 */
@ConditionalOnProperty(name = "external.tourapi.dev-tools", havingValue = "true")
@RequestMapping("/dev")
@Controller
public class DevPageController {
    @GetMapping("/tourapi")
    public String tourapi() {
        return "dev/tourapi";
    }

    /**
     * 던지기 흐름 점검 화면.
     * 세션 생성 → 던지기 → 갈래/말래 → 확정 까지를 실제 API 로 이어서 돌려보고,
     * 추첨을 우회하려는 요청이 제대로 거부되는지도 함께 확인한다.
     */
    @GetMapping("/throw")
    public String throwFlow() {
        return "dev/throw";
    }
}
