/**
 * auth.js
 * 인증 도메인 함수.
 *
 * 백엔드 인증 흐름
 *   POST /api/login → Refresh Token  (응답 헤더 Authorization-Refresh)
 *   POST /api/auth  → Access Token   (응답 헤더 Authorization)
 *   이후 요청        → Authorization 헤더에 Access Token 을 실어 보냄
 *
 * 기존 화면(static/js/auth.js)에서 도메인 함수만 가져왔다.
 * 화면 가드(requireAuth / markAuthState / initHeader)는 DOM 을 직접 만지는 코드라
 * React 쪽 라우트 가드와 컨텍스트가 대신한다.
 */

import { apiFetch, api, reissueAccessToken, ApiError, REFRESH_HEADER } from './api.js';
import { tokenStore } from './token.js';

/* ── 회원가입 ────────────────────────────────────────── */

export function signup(payload) {
    return api.post('/api/user', payload, { auth: false });
}

/* ── 로그인 ──────────────────────────────────────────── */

/**
 * 로그인하고 Refresh Token + Access Token 을 모두 확보한다.
 * 토큰이 응답 "헤더" 로 오기 때문에 본문만 돌려주는 request() 대신 apiFetch() 를 직접 쓴다.
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

export function clearSession() {
    tokenStore.clear();
}

/**
 * 서버의 Refresh Token 을 폐기한 뒤 클라이언트 토큰을 버린다.
 * 서버 호출이 실패하더라도 클라이언트는 반드시 로그아웃된 상태로 끝나야 하므로 예외는 삼킨다.
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
