/**
 * auth.js
 * 인증 도메인 함수 + 화면 진입 가드.
 *
 * 백엔드 인증 흐름
 *   POST /api/login → Refresh Token  (응답 헤더 Authorization-Refresh)
 *   POST /api/auth  → Access Token   (응답 헤더 Authorization)
 *   이후 요청        → Authorization 헤더에 Access Token 을 실어 보냄
 */

import { apiFetch, api, reissueAccessToken, ApiError, REFRESH_HEADER } from './api.js';
import { tokenStore } from './token.js';

/* ── 회원가입 ────────────────────────────────────────── */

/**
 * @param {{username:string, password:string, name?:string, email:string, phone?:string, birth?:string}} payload
 * @returns {Promise<{id:number}>}
 */
export function signup(payload) {
    return api.post('/api/user', payload, { auth: false });
}

/* ── 로그인 ──────────────────────────────────────────── */

/**
 * 로그인하고 Refresh Token + Access Token 을 모두 확보한다.
 * 토큰이 응답 "헤더"로 오기 때문에 본문만 돌려주는 request() 대신 apiFetch() 를 직접 쓴다.
 */
export async function login({ username, password }) {
    const response = await apiFetch('/api/login', {
        method: 'POST',
        body: { username, password },
        auth: false,
    });

    if (!response.ok) {
        // 인증 실패는 Spring Security 가 본문 없이 401 만 내려주므로 여기서 메시지를 만든다
        throw new ApiError(
            response.status,
            response.status === 401
                ? '아이디 또는 비밀번호가 올바르지 않습니다.'
                : '로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.',
        );
    }

    const refreshToken = response.headers.get(REFRESH_HEADER);
    if (!refreshToken) {
        throw new ApiError(500, '서버가 Refresh Token 을 내려주지 않았습니다.');
    }

    tokenStore.setRefresh(refreshToken);

    // 로그인 직후 바로 Access Token 까지 받아둔다
    const issued = await reissueAccessToken();
    if (!issued) {
        tokenStore.clear();
        throw new ApiError(500, 'Access Token 발급에 실패했습니다.');
    }
}

/* ── 로그아웃 ────────────────────────────────────────── */

/**
 * 클라이언트에 저장된 토큰만 버린다.
 * 서버 호출이 이미 끝났거나(탈퇴) 의미가 없을 때(세션 만료) 쓴다.
 */
export function clearSession() {
    tokenStore.clear();
}

/**
 * 로그아웃.
 * 서버의 Refresh Token 을 폐기한 뒤 클라이언트 토큰을 버린다.
 * 서버 호출이 실패하더라도 클라이언트는 반드시 로그아웃된 상태로 끝나야 하므로 예외는 삼킨다.
 * (이미 발급된 Access Token 은 만료(30분)까지 유효하지만 재발급 경로가 끊겨 세션은 그 시점에 끝난다)
 */
export async function logout() {
    try {
        if (tokenStore.hasSession()) await api.post('/api/logout');
    } catch {
        // 네트워크 오류나 토큰 만료. 어차피 아래에서 지운다
    } finally {
        clearSession();
    }
}

/* ── 내 정보 ─────────────────────────────────────────── */

/** id 를 넘기지 않으면 서버가 요청자 본인의 정보를 돌려준다. */
export function fetchMe() {
    return api.get('/api/user');
}

export function updateMe(payload) {
    return api.put('/api/user', payload);
}

/** 탈퇴 (Soft Delete + 개인정보 익명화 + Refresh Token 폐기) */
export function withdraw() {
    return api.del('/api/user', {});
}

/* ── 세션 확인 ───────────────────────────────────────── */

/**
 * 저장된 Refresh Token 이 실제로 살아있는지 서버에 확인한다.
 * @returns {Promise<boolean>}
 */
export async function verifySession() {
    if (!tokenStore.hasSession()) return false;

    if (tokenStore.getAccess()) return true;

    return reissueAccessToken();
}

/* ── 화면 가드 ───────────────────────────────────────── */

/**
 * 로그인 여부를 문서에 반영한다.
 * body[data-auth] 로 헤더 메뉴를 전환하고,
 * data-auth-only / data-guest-only 요소는 hidden 속성으로 실제로 감춘다.
 * @param {'in'|'out'} state
 */
export function markAuthState(state) {
    document.body.dataset.auth = state;

    document.querySelectorAll('[data-auth-only]').forEach((element) => {
        element.hidden = state !== 'in';
    });

    document.querySelectorAll('[data-guest-only]').forEach((element) => {
        element.hidden = state !== 'out';
    });
}

/**
 * 로그인이 필요한 화면에서 호출. 세션이 없으면 로그인 화면으로 보낸다.
 * @returns {Promise<boolean>} 통과 여부
 */
export async function requireAuth(redirectTo = '/user/login') {
    const alive = await verifySession();

    if (!alive) {
        // 이미 죽은 세션이라 서버에 폐기를 요청할 것도 없다
        clearSession();
        window.location.replace(redirectTo);
        return false;
    }

    markAuthState('in');
    return true;
}

/**
 * 로그인/회원가입처럼 이미 로그인한 사용자가 볼 필요 없는 화면에서 호출.
 * @returns {Promise<boolean>} 리다이렉트했으면 true
 */
export async function redirectIfAuthenticated(redirectTo = '/user/mypage') {
    const alive = await verifySession();

    if (alive) {
        window.location.replace(redirectTo);
        return true;
    }

    markAuthState('out');
    return false;
}

/* ── 헤더 ────────────────────────────────────────────── */

/** 공통 헤더의 로그아웃 버튼을 연결한다. 레이아웃을 쓰는 모든 화면에서 한 번씩 호출. */
export function initHeader() {
    document.querySelectorAll('[data-action="logout"]').forEach((button) => {
        button.addEventListener('click', async () => {
            // 폐기 요청이 끝나기 전에 페이지를 떠나면 요청이 취소될 수 있어 기다렸다 이동한다
            button.disabled = true;

            await logout();

            window.location.href = '/user/login';
        });
    });
}
