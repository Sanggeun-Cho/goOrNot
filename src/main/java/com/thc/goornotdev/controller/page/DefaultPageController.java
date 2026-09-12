package com.thc.goornotdev.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("")
@Controller
public class DefaultPageController {
    /**
     * 진입 화면.
     * Access Token 을 요청 헤더로 주고받는 구조라 브라우저가 주소창으로 들어올 때는
     * 서버가 로그인 여부를 알 수 없다. 따라서 화면은 항상 그대로 내려주고,
     * 로그인/마이페이지 분기는 프론트(static/js/page/index.js)에서 처리한다.
     */
    @GetMapping({"/", "/index"})
    public String index() {
        return "index";
    }
}
