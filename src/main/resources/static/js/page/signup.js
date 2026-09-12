/**
 * page/signup.js
 */

import { initHeader, signup, redirectIfAuthenticated } from '../auth.js';
import { bindSubmit, omitEmpty, setFieldError, toast } from '../ui.js';

const form = document.getElementById('signup-form');

initHeader();

bindSubmit(form, async (values) => {
    const { passwordConfirm, ...payload } = values;

    // 비밀번호 확인은 서버가 모르는 값이라 여기서만 검사한다
    if (payload.password !== passwordConfirm) {
        setFieldError(form, 'passwordConfirm', '비밀번호가 일치하지 않습니다.');
        return;
    }

    // 선택 항목을 빈 문자열로 보내지 않고 아예 빼서 보낸다
    await signup(omitEmpty(payload));

    toast('가입이 완료됐어요. 로그인해 주세요.', 'success');
    setTimeout(() => window.location.replace('/user/login'), 900);
});

redirectIfAuthenticated();
