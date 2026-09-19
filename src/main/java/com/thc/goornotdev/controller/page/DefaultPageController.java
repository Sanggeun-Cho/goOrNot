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

    /**
     * React SPA 진입점.
     *
     * 라우팅은 브라우저(react-router)가 하지만, 사용자가 /app/login 을 주소창에 직접 치거나
     * 새로고침하면 그 요청은 서버로 온다. 서버에는 그런 경로가 없으므로 404 가 난다.
     * 그래서 SPA 경로들을 index.html 로 포워딩해 준다.
     *
     * 와일드카드(/app/**)를 쓰지 않는 이유:
     * 번들 파일(/app/assets/*.js)까지 index.html 로 덮어써서 화면이 통째로 안 뜬다.
     * 화면이 늘어날 때마다 여기에 경로를 한 줄씩 추가한다.
     * (App.jsx 의 Route 경로 앞에 '/app' 을 붙인 것과 같아야 한다)
     *
     * /app/category/* 처럼 한 단계짜리 와일드카드는 써도 된다.
     * '*' 는 슬래시를 넘지 않아서 /app/assets/... 를 삼킬 염려가 없다.
     */
    @GetMapping({"/app", "/app/search", "/app/login", "/app/signup", "/app/trips",
            "/app/privacy", "/app/category/*"})
    public String app() {
        return "forward:/app/index.html";
    }
}
