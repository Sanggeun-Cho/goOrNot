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
}
