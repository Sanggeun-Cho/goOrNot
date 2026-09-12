/**
 * page/mypage.js
 * 내 정보 조회 · 수정 · 탈퇴
 */

import { initHeader, requireAuth, fetchMe, updateMe, withdraw, clearSession } from '../auth.js';
import {
    bindSubmit, clearFieldErrors, formatDate, omitEmpty, setText, showApiError, toast,
} from '../ui.js';

const loading = document.getElementById('loading');
const content = document.getElementById('content');
const card = document.getElementById('profile-card');
const form = document.getElementById('profile-form');
const usernameBox = document.getElementById('username-readonly');

let me = null;

initHeader();
bindEvents();
start();

/* ── 초기화 ──────────────────────────────────────────── */

async function start() {
    // 세션이 없으면 이 안에서 로그인 화면으로 보낸다
    if (!(await requireAuth())) return;

    await load();
}

async function load() {
    try {
        me = await fetchMe();
        render(me);

        loading.hidden = true;
        content.hidden = false;
    } catch (error) {
        loading.textContent = '정보를 불러오지 못했습니다.';
        showApiError(null, error);
    }
}

/* ── 렌더링 ──────────────────────────────────────────── */

function render(user) {
    const displayName = user.name || user.username || '';

    setText(document, 'initial', displayName.charAt(0) || '·');
    setText(document, 'displayName', displayName);
    setText(document, 'username', user.username);
    setText(document, 'name', user.name);
    setText(document, 'email', user.email);
    setText(document, 'phone', user.phone);
    setText(document, 'birth', user.birth);
    setText(document, 'createdAt', formatDate(user.createdAt));
}

function fillForm(user) {
    usernameBox.value = user.username ?? '';

    form.elements.name.value = user.name ?? '';
    form.elements.email.value = user.email ?? '';
    form.elements.phone.value = user.phone ?? '';
    form.elements.birth.value = user.birth ?? '';
    form.elements.password.value = '';
}

/* ── 이벤트 ──────────────────────────────────────────── */

function bindEvents() {
    card.querySelector('[data-action="edit"]').addEventListener('click', () => {
        clearFieldErrors(form);
        fillForm(me);
        card.dataset.mode = 'edit';
    });

    form.querySelector('[data-action="cancel"]').addEventListener('click', () => {
        card.dataset.mode = 'view';
    });

    bindSubmit(form, async (values) => {
        // 빈 칸은 빼고 보낸다. 서버 update 는 null 을 "변경 없음"으로 보기 때문.
        const payload = omitEmpty(values);

        if (Object.keys(payload).length === 0) {
            toast('변경할 내용이 없습니다.');
            return;
        }

        await updateMe(payload);

        toast('저장했습니다.', 'success');
        card.dataset.mode = 'view';

        me = await fetchMe();
        render(me);
    });

    document.querySelector('[data-action="withdraw"]').addEventListener('click', onWithdraw);
}

async function onWithdraw(event) {
    const confirmed = window.confirm('정말 탈퇴하시겠어요?\n회원 정보는 즉시 삭제되며 되돌릴 수 없습니다.');
    if (!confirmed) return;

    const button = event.currentTarget;
    button.disabled = true;
    button.classList.add('is-loading');

    try {
        // 탈퇴 API 가 서버의 Refresh Token 까지 폐기하므로 여기서는 로컬만 비운다
        await withdraw();
        clearSession();

        window.location.replace('/');
    } catch (error) {
        showApiError(null, error);

        button.disabled = false;
        button.classList.remove('is-loading');
    }
}
