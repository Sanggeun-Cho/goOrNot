package com.thc.goornotdev.controller.page;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RequestMapping("/user")
@Controller
public class UserPageController {
    /**
     * [2026-09-20] 주소의 일부를 그대로 뷰 이름으로 쓰지 않는다.
     *
     * 원래는 return "user/" + page 였다. 이러면 요청자가 뷰 이름을 정하는 셈인데,
     * Thymeleaf 는 뷰 이름에 '::' 가 들어오면 그 뒤를 프래그먼트 표현식(SpEL)으로 해석한다.
     * 즉 주소창으로 서버에서 식을 돌릴 길이 생긴다(전형적인 템플릿 인젝션 경로).
     * 최신 Thymeleaf 가 일부 막아주긴 하지만, 막아주길 기대하고 열어둘 구멍은 아니다.
     *
     * 실제로 존재하는 화면은 아래 셋뿐이라 화이트리스트로 끊는다.
     * 덤으로 /user/아무거나 가 템플릿 없음 500 이 아니라 404 로 떨어진다.
     */
    private static final Set<String> PAGES = Set.of("login", "signup", "mypage");

    @GetMapping("/{page}")
    public String page(@PathVariable String page) {
        return view(page);
    }

    @GetMapping("/{page}/{id}")
    public String page2(@PathVariable String page, @PathVariable String id) {
        return view(page);
    }

    private String view(String page) {
        if (!PAGES.contains(page)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "user/" + page;
    }
}