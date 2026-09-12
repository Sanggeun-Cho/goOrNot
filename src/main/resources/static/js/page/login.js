/**
 * page/login.js
 */

import { initHeader, login, redirectIfAuthenticated } from '../auth.js';
import { bindSubmit, setFieldError } from '../ui.js';

const form = document.getElementById('login-form');

initHeader();

bindSubmit(form, async ({ username, password }) => {
    // 서버를 왕복할 필요 없는 검사만 여기서 처리한다
    if (!username) {
        setFieldError(form, 'username', '아이디를 입력해 주세요.');
        return;
    }

    if (!password) {
        setFieldError(form, 'password', '비밀번호를 입력해 주세요.');
        return;
    }

    await login({ username, password });

    // 뒤로가기로 로그인 화면에 다시 오지 않도록 replace 사용
    window.location.replace('/user/mypage');
});

// 이미 로그인한 상태면 마이페이지로
redirectIfAuthenticated();
