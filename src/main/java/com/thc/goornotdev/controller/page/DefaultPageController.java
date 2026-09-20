package com.thc.goornotdev.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("")
@Controller
public class DefaultPageController {
    /**
     * 진입 화면 → 실제 서비스(/app)로 넘긴다.
     *
     * [2026-09-20] 원래 여기서 1차 Thymeleaf 화면(templates/index.html)을 내려줬다.
     * 심사위원은 제출한 URL 을 그냥 한 번 누르고, 그때 뜬 화면이 곧 첫인상이 된다.
     * 실제 서비스는 React 쪽(/app)이므로 루트는 거기로 보낸다.
     *
     * 301(영구)이 아니라 302(임시)인 이유:
     * redirect: 접두사의 기본값이 302 다. 301 로 바꾸면 브라우저가 캐시에 박아버려서
     * 되돌릴 때 사용자 브라우저를 일일이 비워야 한다. 루트 경로는 그럴 위험을 질 이유가 없다.
     *
     * Thymeleaf 화면 파일 자체는 그대로 둔다. 지우는 건(기획안 C안) 제출 이틀 전에 할 일이 아니고,
     * 남겨둬도 이제 여기로 들어오는 길이 없다. /user/** 는 직접 주소를 친 경우에만 열린다.
     */
    @GetMapping({"/", "/index"})
    public String index() {
        return "redirect:/app";
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
            "/app/me", "/app/wishlist", "/app/privacy", "/app/category/*", "/app/cards/*/*"})
    public String app() {
        return "forward:/app/index.html";
    }
}
